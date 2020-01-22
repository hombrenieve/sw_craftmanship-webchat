package es.sidelab.webchat;

import es.codeurjc.webchat.Chat;
import es.codeurjc.webchat.User;

import java.util.concurrent.CountDownLatch;

public class ConcurrentTestUser implements User {
    private String data;
    private CountDownLatch cl;

    ConcurrentTestUser(CountDownLatch cl) {
        this.cl = cl;
    }

    public String getData() {
        return this.data;
    }

    protected void setData(String newData) {
        this.data = newData;
    }

    protected void countDown() {
        this.cl.countDown();
    }

    @Override
    public String getName() {
        return "user-"+Thread.currentThread().getName();
    }

    @Override
    public String getColor() {
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
}
