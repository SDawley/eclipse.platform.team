/*******************************************************************************
 * Copyright (c) 2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial implementation
 ******************************************************************************/
package org.eclipse.team.internal.ccvs.core.syncinfo;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.util.Assert;

/**
 * Provides a per-thread nested locking mechanism. A thread can acquire a
 * lock and then call acquire() multiple times. Other threads that try
 * and acquire the lock will be blocked until the first thread releases all
 * it's nested locks.
 */
public class ReentrantLock {

	private final static boolean DEBUG = Policy.DEBUG_THREADING;
	private Thread thread;
	private int nestingCount;
	
	private Set readOnlyThreads = new HashSet();
	
	public ReentrantLock() {
		this.thread = null;
		this.nestingCount = 0;
	}
	
	public synchronized void acquire() {
		// stop early if we've been interrupted -- don't enter the lock anew
		Thread thisThread = Thread.currentThread();

		// race for access to the lock -- does not guarantee fairness
		if (thread != thisThread) {
			while (nestingCount != 0) {
				try {
					if(DEBUG) System.out.println("["+ thisThread.getName() + "] waiting for CVS synchronizer lock"); //$NON-NLS-1$ //$NON-NLS-2$
					wait();
				} catch(InterruptedException e) {
					// keep waiting for the lock
					if(DEBUG) System.out.println("["+ thisThread.getName() + "] interrupted in CVS synchronizer lock"); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			thread = thisThread;
			if(DEBUG) System.out.println("[" + thisThread.getName() + "] acquired CVS synchronizer lock"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		nestingCount++;
	}
	
	public synchronized void release() {
		Thread thisThread = Thread.currentThread();
		Assert.isLegal(thread == thisThread,
			"Thread attempted to release a lock it did not own"); //$NON-NLS-1$
		if (--nestingCount == 0) {
			if(DEBUG) System.out.println("[" + thread.getName() + "] released CVS synchronizer lock"); //$NON-NLS-1$ //$NON-NLS-2$
			thread = null;
			notifyAll();
		}
	}
	
	public int getNestingCount() {
		Thread thisThread = Thread.currentThread();
		Assert.isLegal(thread == thisThread,
			"Thread attempted to read nesting count of a lock it did not own"); //$NON-NLS-1$
		return nestingCount;
	}
	
	public boolean isReadOnly() {
		return readOnlyThreads.contains(thread);
	}
	
	public void addReadOnlyThread(Thread thread) {
		readOnlyThreads.add(thread);
	}
}
