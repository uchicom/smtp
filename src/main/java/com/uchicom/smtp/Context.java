// (c) 2016 uchicom
package com.uchicom.smtp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Context {

	private static final Context context = new Context();
	private Context() {

	}
	public static Context singleton() {
		return context;
	}

	/** ユーザー名一覧 */
	private String[] users;
	private Map<String, List<Mail>> boxMap;

	public String[] getUsers() {
		return users;
	}
	public void setUsers(String[] users) {
		this.users = users;
		boxMap = new HashMap<>();
		for (String user : users) {
			boxMap.put(user, new ArrayList<Mail>());
		}
	}

	public void addMail(String user, Mail mail) {
		boxMap.get(user).add(mail);
	}
	public List<Mail> getMailList(String user) {
		if (boxMap != null) {
			return boxMap.get(user);
		}
		return null;
	}
	public Map<String, List<Mail>> getBoxMap() {
		return boxMap;
	}
	public void setBoxMap(Map<String, List<Mail>> boxMap) {
		this.boxMap = boxMap;
	}
}
