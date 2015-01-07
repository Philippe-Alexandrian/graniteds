/**
 *   GRANITE DATA SERVICES
 *   Copyright (C) 2006-2015 GRANITE DATA SERVICES S.A.S.
 *
 *   This file is part of the Granite Data Services Platform.
 *
 *   Granite Data Services is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 *
 *   Granite Data Services is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 *   General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 *   USA, or see <http://www.gnu.org/licenses/>.
 */
package org.granite.tide.spring;

import org.granite.tide.TideTransactionManager;
import org.granite.tide.data.TideTransactionPersistenceManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Responsible for attaching a session with the persistence mangager
 * @author cingram
 *
 */
public class SpringTransactionManager implements TideTransactionManager {
	
	private PlatformTransactionManager transactionManager;
	
	private static final TransactionDefinition TX_DEF = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED);
	static {
		((DefaultTransactionDefinition)TX_DEF).setReadOnly(true);
	}
	
	
	public SpringTransactionManager(PlatformTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}
	
	public Object begin(TideTransactionPersistenceManager pm) {
		return transactionManager.getTransaction(TX_DEF);
	}

	public void commit(Object tx) throws Exception {
		transactionManager.commit(((TransactionStatus)tx));
	}

	public void rollback(Object tx) {
		transactionManager.rollback(((TransactionStatus)tx));
	}
}
