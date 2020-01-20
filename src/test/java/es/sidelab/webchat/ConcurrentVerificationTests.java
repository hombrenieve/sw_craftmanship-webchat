package es.sidelab.webchat;

import es.codeurjc.webchat.Chat;
import es.codeurjc.webchat.ChatManager;
import es.codeurjc.webchat.User;
import org.junit.Test;

import java.util.ConcurrentModificationException;
import java.util.concurrent.*;

import static org.junit.Assert.assertTrue;

public class ConcurrentVerificationTests {
    final int numUsers = 5;
    ChatManager manager = new ChatManager(2);
    CountDownLatch clStart = new CountDownLatch(numUsers);
    CountDownLatch cl = new CountDownLatch(numUsers);
    CountDownLatch clFinish = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(numUsers+1);
    CompletionService<String> service = new ExecutorCompletionService<>(executor);

    private void submit(Callable<String> senderActions, Callable<String> receiverActions) {
        for(int i = 0; i < numUsers; i++) {
            service.submit(receiverActions);
        }
        service.submit(senderActions);
    }

    private void checkTasks(String assertMsg, String expected) throws InterruptedException {
        try {
            for(int i = 0; i < numUsers; i++) {
                Future<String> f = service.take();
                String received = f.get();
                assertTrue(assertMsg, received.equals(expected));
            }
        } catch (ExecutionException ee) {
            throw new ConcurrentModificationException(ee.getCause());
        }
    }

    @Test
    public void newChat() throws InterruptedException {
        final String chatName = "NewChatTest";

        Callable<String> receiverActions = () -> {
            final String[] receivedChatName = new String[1];
            TestUser user = new TestUser("user-"+Thread.currentThread().getName()) {
                @Override
                public void newChat(Chat chat) {
                    receivedChatName[0] = chat.getName();
                    cl.countDown();
                }
            };
            manager.newUser(user);
            clStart.countDown();
            clFinish.await();
            return receivedChatName[0];
        };

        Callable<String> senderActions = () -> {
            TestUser user = new TestUser("sender");
            manager.newUser(user);
            clStart.await();
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            cl.await();
            clFinish.countDown();
            return chat.getName();
        };

        this.submit(senderActions, receiverActions);

        this.checkTasks("Chat created is not correct", chatName);
    }

    @Test
    public void deleteChat() throws InterruptedException {
        final String chatName = "DeleteChatTest";

        Callable<String> receiverActions = () -> {
            final String[] receivedChatName = new String[1];
            TestUser user = new TestUser("user-"+Thread.currentThread().getName()) {
                @Override
                public void chatClosed(Chat chat) {
                    receivedChatName[0] = chat.getName();
                    cl.countDown();
                }
            };
            manager.newUser(user);
            clStart.countDown();
            clFinish.await();
            return receivedChatName[0];
        };

        Callable<String> senderActions = () -> {
            TestUser user = new TestUser("sender");
            manager.newUser(user);
            clStart.await();
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            manager.closeChat(chat);
            cl.await();
            clFinish.countDown();
            return chat.getName();
        };

        this.submit(senderActions, receiverActions);

        this.checkTasks("Chat deleted is not correct", chatName);
    }

    @Test
    public void newUserInChat() throws InterruptedException {
        final String chatName = "NewUserChatTest";
        final String userName = "VIPUser";

        Callable<String> receiverActions = () -> {
            final String[] receivedUserName = new String[1];
            TestUser user = new TestUser("user-"+Thread.currentThread().getName()) {
                @Override
                public void newUserInChat(Chat chat, User user) {
                    receivedUserName[0] = user.getName();
                    cl.countDown();
                }
            };
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clStart.countDown();
            clFinish.await();
            return receivedUserName[0];
        };

        Callable<String> senderActions = () -> {
            TestUser user = new TestUser(userName);
            manager.newUser(user);
            clStart.await();
            cl = new CountDownLatch(numUsers);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            cl.await();
            clFinish.countDown();
            return user.getName();
        };

        this.submit(senderActions, receiverActions);

        this.checkTasks("New user in chat is not correct", userName);
    }
}
