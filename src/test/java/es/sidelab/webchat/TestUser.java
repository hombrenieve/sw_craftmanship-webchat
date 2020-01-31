package es.sidelab.webchat;

import es.codeurjc.webchat.Chat;
import es.codeurjc.webchat.User;

public class TestUser implements User {

	public String name;

	public TestUser(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}
	
	public String getColor(){
		return "007AFF";
	}

	@Override
	public void newChat(Chat chat) {
	}

	@Override
	public void chatClosed(Chat chat) {
	}

	@Override
	public void newUserInChat(Chat chat, User user) {
	}

	@Override
	public void userExitedFromChat(Chat chat, User user) {
	}

	@Override
	public void newMessage(Chat chat, User user, String message) {
	}

	@Override
	public String toString() {
		return "User[" + name + "]";
	}	
}
