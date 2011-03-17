/*
 * Part of the CCNx Java Library.
 *
 * Copyright (C) 2011 Palo Alto Research Center, Inc.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation. 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. You should have received
 * a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.ccnx.ccn.profiles.ccnd;

import java.io.IOException;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.CCNInterestListener;
import org.ccnx.ccn.config.SystemConfiguration;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;

public class CCNDCacheManager implements CCNInterestListener {
	
	public void clearCache(ContentName prefix, CCNHandle handle, long timeout) throws IOException {
		Interest interest = new Interest(prefix);
		interest.answerOriginKind(Interest.DEFAULT_ANSWER_ORIGIN_KIND + Interest.MARK_STALE);
		interest.scope(0);
		long currentTime = System.currentTimeMillis();
		long endTime = currentTime + timeout;
		boolean noTimeout = timeout == SystemConfiguration.NO_TIMEOUT;
		while (noTimeout || currentTime < endTime) {
			handle.expressInterest(interest, this);
			synchronized (this) {
				try {
					this.wait(SystemConfiguration.SHORT_TIMEOUT);
				} catch (InterruptedException e) {}
			}
			long prevTime = currentTime;
			currentTime = System.currentTimeMillis();
			if (currentTime - prevTime > SystemConfiguration.SHORT_TIMEOUT)
				return;
		}
		throw new IOException("Clear Cache timed out before completion");
	}

	public Interest handleContent(ContentObject data, Interest interest) {
		synchronized (this) {
			this.notifyAll();
		}
		return interest;
	}
}
