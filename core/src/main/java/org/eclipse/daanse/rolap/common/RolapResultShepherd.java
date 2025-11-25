/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */


package org.eclipse.daanse.rolap.common;

import java.text.MessageFormat;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.daanse.olap.api.Execution;
import org.eclipse.daanse.olap.api.ResultShepherd;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.QueryCanceledException;
import org.eclipse.daanse.olap.common.QueryTimeoutException;
import org.eclipse.daanse.olap.common.ResourceLimitExceededException;
import org.eclipse.daanse.olap.common.Util;
import  org.eclipse.daanse.olap.util.Pair;

/**
 * A utility class for Connection. It specializes in
 * shepherding the creation of RolapResult by running the actual execution
 * on a separate thread from the user thread so we can:
 *
 * Monitor all executions for timeouts and resource limits as they run
 * in the background
 * Bubble exceptions to the user thread as fast as they happen.
 * Gracefully cancel all SQL statements and cleanup in the background.
 *
 *
 * @author LBoudreau
 */
public class RolapResultShepherd implements ResultShepherd {

    /**
     * An executor service used for both the shepherd thread and the
     * Execution objects.
     */
    private final ExecutorService executor;

    /**
     * List of tasks that should be monitored by the shepherd thread.
     */
    private final List<Pair<FutureTask<Result>, Execution>> tasks =
        new CopyOnWriteArrayList<>();

    private final Timer timer =
    		new Timer("daanse.rolap.RolapResultShepherd#timer", true);

    public RolapResultShepherd(final long rolapConnectionShepherdThreadPollingInterval, TimeUnit rolapConnectionShepherdThreadPollingIntervalUnit, final int rolapConnectionShepherdNbThreads) {

        final int maximumPoolSize = rolapConnectionShepherdNbThreads;
        executor =
            Util.getExecutorService(
                 // We use the same value for coreSize and maxSize
                // because that's the behavior we want. All extra
                // tasks will be put on an unbounded queue.
                maximumPoolSize,
                maximumPoolSize,
                1000000,
                "daanse.rolap.RolapResultShepherd$executor",
                new RejectedExecutionHandler() {
                    private final static String queryLimitReached = """
                    The number of concurrent MDX statements that can be processed simultaneously by this Daanse server instance ({0,number}) has been reached. To change the limit, set the ''{1}'' property.
                    """;

                    @Override
					public void rejectedExecution(
                        Runnable r,
                        ThreadPoolExecutor executor)
                    {
                        throw new OlapRuntimeException(MessageFormat.format(queryLimitReached,
                            maximumPoolSize,
                            "rolapConnectionShepherdNbThreads"));
                    }
                });

        long period = rolapConnectionShepherdThreadPollingIntervalUnit.toMillis(rolapConnectionShepherdThreadPollingInterval);
        timer.schedule(
            new TimerTask() {
                @Override
				public void run() {
                    for (final Pair<FutureTask<Result>, Execution> task
                        : tasks)
                    {
                        if (task.left.isDone()) {
                            tasks.remove(task);
                            continue;
                        }
                        if (task.right.isCancelOrTimeout()) {
                            // Remove it from the list so that we know
                            // it was cleaned once.
                            tasks.remove(task);

                            // Cancel the FutureTask for which
                            // the user thread awaits. The user
                            // thread will call
                            // Execution.checkCancelOrTimeout
                            // later and take care of sending
                            // an exception on the user thread.
                            task.left.cancel(false);
                        }
                    }
                }
            },
            period,
            period);
    }

    /**
     * Executes and shepherds the execution of an Execution instance.
     * The shepherd will wrap the Execution instance into a Future object
     * which can be monitored for exceptions. If any are encountered,
     * two things will happen. First, the user thread will be returned and
     * the resulting exception will bubble up. Second, the execution thread
     * will attempt to do a graceful stop of all running SQL statements and
     * release all other resources gracefully in the background.
     * @param execution An Execution instance.
     * @param callable A callable to monitor returning a Result instance.
     * @throws ResourceLimitExceededException if some resource limit specified
     * in the property file was exceeded
     * @throws QueryCanceledException if query was canceled during execution
     * @throws QueryTimeoutException if query exceeded timeout specified in
     * the property file
     * @return A Result object, as supplied by the Callable passed as a
     * parameter.
     */
    @Override
	public Result shepherdExecution(
        Execution execution,
        Callable<Result> callable)
    {
        // We must wrap this execution into a task that so that we are able
        // to monitor, cancel and detach from it.
        FutureTask<Result> task = new FutureTask<>(callable);

        // Register this task with the shepherd thread
        final Pair<FutureTask<Result>, Execution> pair =
            new Pair<>(
                task,
                execution);
        tasks.add(pair);

        try {
            // Now run it.
            executor.execute(task);
            return task.get();
        } catch (Throwable e) {
            // Make sure to clean up pending SQL queries.
            execution.cancelSqlStatements();

            // Make sure to propagate the interruption flag.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            // Unwrap any java.concurrent wrappers.
            Throwable node = e;
            if (e instanceof ExecutionException executionException) {
                node = executionException.getCause();
            }

            // Let the Execution throw whatever it wants to, this way the
            // API contract is respected. The program should in most cases
            // stop here as most exceptions will originate from the Execution
            // instance.
            execution.checkCancelOrTimeout();

            // We must also check for ResourceLimitExceededExceptions,
            // which might be wrapped by an ExecutionException. In order to
            // respect the API contract, we must throw the cause, not the
            // wrapper.
            final ResourceLimitExceededException t =
                Util.getMatchingCause(
                    node, ResourceLimitExceededException.class);
            if (t != null) {
                throw t;
            }

            // Check for Mondrian exceptions in the exception chain.
            // we can throw these back as-is.
            final OlapRuntimeException m =
                Util.getMatchingCause(
                    node, OlapRuntimeException.class);
            if (m != null) {
                // Throw that.
                throw m;
            }

            // Since we got here, this means that the exception was
            // something else. Just wrap/throw.
            if (node instanceof RuntimeException) {
                throw (RuntimeException) node;
            } else if (node instanceof Error) {
                throw (Error) node;
            } else {
                throw new OlapRuntimeException(node);
            }
        }
    }

    @Override
	public void shutdown() {
        this.timer.cancel();
        this.executor.shutdown();
        this.tasks.clear();
    }
}

// End RolapResultShepherd.java

