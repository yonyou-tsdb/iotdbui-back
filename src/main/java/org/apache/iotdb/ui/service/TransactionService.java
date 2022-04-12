/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.ui.service;

import java.util.LinkedList;
import java.util.List;

import org.apache.iotdb.ui.condition.ConnectCondition;
import org.apache.iotdb.ui.condition.EmailLogCondition;
import org.apache.iotdb.ui.condition.QueryCondition;
import org.apache.iotdb.ui.entity.Connect;
import org.apache.iotdb.ui.entity.EmailLog;
import org.apache.iotdb.ui.entity.Query;
import org.apache.iotdb.ui.entity.User;
import org.apache.iotdb.ui.exception.BaseException;
import org.apache.iotdb.ui.exception.FeedbackError;
import org.apache.iotdb.ui.face.ConnectFace;
import org.apache.iotdb.ui.mapper.ConnectDao;
import org.apache.iotdb.ui.mapper.EmailLogDao;
import org.apache.iotdb.ui.mapper.QueryDao;
import org.apache.iotdb.ui.mapper.UserDao;
import org.apache.iotdb.ui.model.EmailLogStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

	@Autowired
	private ConnectDao connectDao;

	@Autowired
	private ConnectService connectService;

	@Autowired
	private QueryDao queryDao;

	@Autowired
	private UserDao userDao;

	@Autowired
	private EmailLogDao emailLogDao;

	@Transactional(value = "transactionManager1", rollbackFor = {
			BaseException.class }, readOnly = false, propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
	public int insertConnectTransactive(Connect connect, Long userId) throws BaseException {
		int ret = connectDao.insert(connect);
		Connect c = new Connect();
		c.setAlias(connect.getAlias());
		c.setUserId(userId);
		int n = connectDao.count(c);
		if (n != 1) {
			throw new BaseException(FeedbackError.ALIAS_REPEAT, FeedbackError.ALIAS_REPEAT_MSG);
		}
		return ret;
	}

	@Transactional(value = "transactionManager1", rollbackFor = {
			BaseException.class }, readOnly = false, propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
	public int updateAccountTransactive(Connect connect, Long userId) throws BaseException {
		int ret = connectDao.update(connect);
		Connect c = new Connect();
		c.setAlias(connect.getAlias());
		c.setUserId(userId);
		int n = connectDao.count(c);
		if (n != 1) {
			throw new BaseException(FeedbackError.ALIAS_REPEAT, FeedbackError.ALIAS_REPEAT_MSG);
		}
		return ret;
	}

	@Transactional(value = "transactionManager1", rollbackFor = {
			BaseException.class }, readOnly = false, propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
	public int insertQueryTransactive(Query query, Long connectId) throws BaseException {
		int ret = queryDao.insert(query);
		Query q = new Query();
		q.setName(query.getName());
		q.setConnectId(connectId);
		int n = queryDao.count(q);
		if (n != 1) {
			throw new BaseException(FeedbackError.QUERY_EXIST, FeedbackError.QUERY_EXIST_MSG);
		}
		return ret;
	}

	@Transactional(value = "transactionManager1", rollbackFor = {
			BaseException.class }, readOnly = false, propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
	public int insertEmailLogTransactive(EmailLog emailLog) throws BaseException {
		int ret = emailLogDao.insert(emailLog);
		EmailLogCondition elc = new EmailLogCondition();
		elc.setEmail(emailLog.getEmail());
		elc.setAvailable(false);
		elc.setStatus(EmailLogStatus.INSERT);
		int n = emailLogDao.count(elc);
		if (n > 0) {
			throw new BaseException(FeedbackError.ACCOUNT_REGISTER_ERROR, FeedbackError.ACCOUNT_REGISTER_ERROR_MSG);
		}
		User u = new User();
		u.setName(emailLog.getTempAccount());
		n = userDao.count(u);
		if (n > 0) {
			throw new BaseException(FeedbackError.ACCOUNT_REGISTER_ERROR, FeedbackError.ACCOUNT_REGISTER_ERROR_MSG);
		}
		return ret;
	}

	@Transactional(value = "transactionManager1", rollbackFor = {
			BaseException.class }, readOnly = false, propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
	public int insertUserTransactive(User user, EmailLog emailLog) throws BaseException {
		int ret = userDao.insert(user);
		EmailLogCondition elc = new EmailLogCondition();
		elc.setEmailEqualOrUsernameEqual(emailLog.getEmail(), emailLog.getTempAccount());
		elc.setAvailable(false);
		elc.setStatus(EmailLogStatus.INSERT);
		int n = emailLogDao.count(elc);
		if (n > 0) {
			throw new BaseException(FeedbackError.ACCOUNT_ACTIVATE_ERROR, FeedbackError.ACCOUNT_ACTIVATE_ERROR_MSG);
		}
		return ret;
	}

	@Transactional(value = "transactionManager1", rollbackFor = {
			BaseException.class }, readOnly = false, propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE)
	public int deleteUserTransactive(User user) throws BaseException {
		connectService.loadUser(user, new Connect());
		QueryCondition qc = new QueryCondition();
		List<Long> connectIdList = new LinkedList<>();
		for (ConnectFace e : user.getConnect()) {
			connectIdList.add(((Connect) e).getId());
		}
		qc.setConnectIdIn(connectIdList);
		queryDao.delete(qc);

		ConnectCondition cc = new ConnectCondition();
		cc.setUserIdEqual(user.getId());
		connectDao.delete(cc);

		int ret = userDao.delete(user);

		try {
			EmailLogCondition elc = new EmailLogCondition();
			elc.setAccountIdEqual(user.getId());
			emailLogDao.delete(elc);
			elc.setAccountIdEqual(null);
			elc.setTempAccountEqual(user.getName());
			emailLogDao.delete(elc);
		} catch (Exception e) {
		}
		return ret;
	}
}
