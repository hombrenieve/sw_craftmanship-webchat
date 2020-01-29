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
    private ChatManager manager = new ChatManager(2);
    private CountDownLatch clStart = new CountDownLatch(numUsers);
    private CountDownLatch cl = new CountDownLatch(numUsers);
    private CountDownLatch clFinish = new CountDownLatch(1);
    private ExecutorService executor = Executors.newFixedThreadPool(numUsers+1);
    private CompletionService<String> service = new ExecutorCompletionService<>(executor);

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
            ConcurrentTestUser user = new ConcurrentTestUser(cl) {
                @Override
                public void newChat(Chat chat) {
                    this.setData(chat.getName());
                    this.countDown();
                }
            };
            manager.newUser(user);
            clStart.countDown();
            clFinish.await();
            return user.getData();
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
            ConcurrentTestUser user = new ConcurrentTestUser(cl) {
                @Override
                public void chatClosed(Chat chat) {
                    this.setData(chat.getName());
                    this.countDown();
                }
            };
            manager.newUser(user);
            clStart.countDown();
            clFinish.await();
            return user.getData();
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
           ConcurrentTestUser user = new ConcurrentTestUser(cl) {
                @Override
                public void newUserInChat(Chat chat, User user) {
                    this.setData(user.getName());
                    if(clStart.getCount() == 0) this.countDown();
                }
            };
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clStart.countDown();
            clFinish.await();
            return user.getData();
        };

        Callable<String> senderActions = () -> {
            TestUser user = new TestUser(userName);
            manager.newUser(user);
            clStart.await();
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            cl.await();
            clFinish.countDown();
            return user.getName();
        };

        this.submit(senderActions, receiverActions);

        this.checkTasks("New user in chat is not correct", userName);
    }

    @Test
    public void userExitedFromChat() throws InterruptedException {
        final String chatName = "ExitedUserChatTest";
        final String userName = "VIPUser";

        Callable<String> receiverActions = () -> {
            ConcurrentTestUser user = new ConcurrentTestUser(cl) {
                @Override
                public void userExitedFromChat(Chat chat, User user) {
                    this.setData(user.getName());
                    this.countDown();
                }
            };
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clStart.countDown();
            clFinish.await();
            return user.getData();
        };

        Callable<String> senderActions = () -> {
            TestUser user = new TestUser(userName);
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clStart.await();
            chat.removeUser(user);
            cl.await();
            clFinish.countDown();
            return user.getName();
        };

        this.submit(senderActions, receiverActions);

        this.checkTasks("Deleted user in chat is not correct", userName);
    }

    @Test
    public void newMessageInChat() throws InterruptedException {
        final String chatName = "NewMessageInChatTest";
        final String userName = "VIPUser";
        final String viMessage = "Hello";

        Callable<String> receiverActions = () -> {
            ConcurrentTestUser user = new ConcurrentTestUser(cl) {
                @Override
                public void newMessage(Chat chat, User user, String message) {
                    this.setData(message);
                    this.countDown();
                }
            };
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clStart.countDown();
            clFinish.await();
            return user.getData();
        };

        Callable<String> senderActions = () -> {
            TestUser user = new TestUser(userName);
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clStart.await();
            chat.sendMessage(user, viMessage);
            cl.await();
            clFinish.countDown();
            return viMessage;
        };

        this.submit(senderActions, receiverActions);

        this.checkTasks("Message received in chat is not correct", viMessage);
    }
}
