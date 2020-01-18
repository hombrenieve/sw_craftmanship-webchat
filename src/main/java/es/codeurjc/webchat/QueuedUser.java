package es.codeurjc.webchat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QueuedUser implements User {
    private User user;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    QueuedUser(User user) {
        this.user = user;
    }

    @Override
    public String getName() {
        return this.user.getName();
    }

    @Override
    public String getColor() {
        return this.user.getColor();
    }

    @Override
    public void newChat(Chat chat) {
        executor.submit(() -> this.user.newChat(chat));
    }

    @Override
    public void chatClosed(Chat chat) {
        executor.submit(() -> this.user.chatClosed(chat));
    }

    @Override
    public void newUserInChat(Chat chat, User user) {
        executor.submit(() -> this.user.newUserInChat(chat, user));
    }

    @Override
    public void userExitedFromChat(Chat chat, User user) {
        executor.submit(() -> this.user.userExitedFromChat(chat, user));
    }

    @Override
    public void newMessage(Chat chat, User user, String message) {
        executor.submit(() -> this.user.newMessage(chat, user, message));
    }
}
