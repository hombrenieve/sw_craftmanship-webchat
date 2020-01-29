package es.codeurjc.webchat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ChatManager {

	private ConcurrentHashMap<String, Chat> chats = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
	private int maxChats;
	private Semaphore chatSemaphore;

	public ChatManager(int maxChats) {
		this.maxChats = maxChats;
		this.chatSemaphore =  new Semaphore(this.maxChats);
	}

	public void newUser(User user) {

		User oldUser = users.putIfAbsent(user.getName(), new QueuedUser(user));
		if(oldUser != null){
			throw new IllegalArgumentException("There is already a user with name \'"
					+ user.getName() + "\'");
		}
	}

	public Chat newChat(String name, long timeout, TimeUnit unit) throws InterruptedException,
			TimeoutException {

		Chat newChat = new Chat(this, name);
		Chat oldChat = chats.putIfAbsent(name, newChat);
		if(oldChat != null){
			return oldChat;
		}

		if (!this.chatSemaphore.tryAcquire(timeout, unit)) {
			this.chats.remove(name);
			throw new TimeoutException("There is no enough capacity to create a new chat");
		}

		for(User user : users.values()){
			user.newChat(newChat);
		}

		return newChat;
	}

	public void closeChat(Chat chat) {
		if (!chats.remove(chat.getName(), chat)) {
			throw new IllegalArgumentException("Trying to remove an unknown chat with name \'"
					+ chat.getName() + "\'");
		}
		this.chatSemaphore.release();

		for(User user : users.values()){
			user.chatClosed(chat);
		}
	}

	public Collection<Chat> getChats() {
		return Collections.unmodifiableCollection(chats.values());
	}

	public Chat getChat(String chatName) {
		return chats.get(chatName);
	}

	public Collection<User> getUsers() {
		return Collections.unmodifiableCollection(users.values());
	}

	public User getUser(String userName) {
		return users.get(userName);
	}

	public void close() {}
}
