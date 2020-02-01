package es.sidelab.webchat;

import es.codeurjc.webchat.Chat;
import es.codeurjc.webchat.ChatManager;
import es.codeurjc.webchat.User;
import org.junit.Test;

import java.util.ConcurrentModificationException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

public class ConcurrentVerificationTests {
    final int numUsers = 5;
    private ChatManager manager = new ChatManager(2);
    private CountDownLatch clReadyToStart = new CountDownLatch(numUsers);
    private CountDownLatch clUserReceivedEvent = new CountDownLatch(numUsers);
    private CountDownLatch clReadyToFinish = new CountDownLatch(1);
    private ExecutorService executor = Executors.newFixedThreadPool(numUsers+1);
    private CompletionService<String> service = new ExecutorCompletionService<>(executor);

    private class ConcurrentTestUser extends TestUser {
        private String data;
        private CountDownLatch cl;

        ConcurrentTestUser(CountDownLatch cl) {
            super(Thread.currentThread().getName());
            this.cl = cl;
        }

        public String getData() {
            return this.data;
        }

        protected void setData(String newData) {
            this.data = newData;
        }

        protected void eventReceived() {
            this.cl.countDown();
        }
    }

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
                assertTrue(assertMsg+": "+received, received.equals(expected));
            }
        } catch (ExecutionException ee) {
            throw new ConcurrentModificationException(ee.getCause());
        }
    }

    @Test
    public void newChat() throws InterruptedException {
        final String chatName = "NewChatTest";

        Callable<String> receiverActions = () -> {
            ConcurrentTestUser user = new ConcurrentTestUser(clUserReceivedEvent) {
                @Override
                public void newChat(Chat chat) {
                    this.setData(chat.getName());
                    this.eventReceived();
                }
            };
            manager.newUser(user);
            clReadyToStart.countDown();
            clReadyToFinish.await();
            return user.getData();
        };

        Callable<String> senderActions = () -> {
            TestUser user = new TestUser("sender");
            manager.newUser(user);
            clReadyToStart.await();
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            clUserReceivedEvent.await();
            clReadyToFinish.countDown();
            return chat.getName();
        };

        this.submit(senderActions, receiverActions);

        this.checkTasks("Chat created is not correct", chatName);
    }

    @Test
    public void deleteChat() throws InterruptedException {
        final String chatName = "DeleteChatTest";

        Callable<String> receiverActions = () -> {
            ConcurrentTestUser user = new ConcurrentTestUser(clUserReceivedEvent) {
                @Override
                public void chatClosed(Chat chat) {
                    this.setData(chat.getName());
                    this.eventReceived();
                }
            };
            manager.newUser(user);
            clReadyToStart.countDown();
            clReadyToFinish.await();
            return user.getData();
        };

        Callable<String> senderActions = () -> {
            TestUser user = new TestUser("sender");
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            clReadyToStart.await();
            manager.closeChat(chat);
            clUserReceivedEvent.await();
            clReadyToFinish.countDown();
            return chat.getName();
        };

        this.submit(senderActions, receiverActions);

        this.checkTasks("Chat deleted is not correct", chatName);
    }

    @Test
    public void newUserInChat() throws InterruptedException {
        final String chatName = "NewUserChatTest";
        final String userName = "VIPUser";
        AtomicBoolean userAdded = new AtomicBoolean(false);

        Callable<String> receiverActions = () -> {
           ConcurrentTestUser user = new ConcurrentTestUser(clUserReceivedEvent) {
                @Override
                public void newUserInChat(Chat chat, User user) {
                    if(userAdded.get()) {
                        this.setData(user.getName());
                        this.eventReceived();
                    }
                }
            };
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clReadyToStart.countDown();
            clReadyToFinish.await();
            return user.getData();
        };

        Callable<String> senderActions = () -> {
            TestUser user = new TestUser(userName);
            manager.newUser(user);
            clReadyToStart.await();
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            userAdded.set(true);
            chat.addUser(user);
            clUserReceivedEvent.await();
            clReadyToFinish.countDown();
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
            ConcurrentTestUser user = new ConcurrentTestUser(clUserReceivedEvent) {
                @Override
                public void userExitedFromChat(Chat chat, User user) {
                    this.setData(user.getName());
                    this.eventReceived();
                }
            };
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clReadyToStart.countDown();
            clReadyToFinish.await();
            return user.getData();
        };

        Callable<String> senderActions = () -> {
            TestUser user = new TestUser(userName);
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clReadyToStart.await();
            chat.removeUser(user);
            clUserReceivedEvent.await();
            clReadyToFinish.countDown();
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
            ConcurrentTestUser user = new ConcurrentTestUser(clUserReceivedEvent) {
                @Override
                public void newMessage(Chat chat, User user, String message) {
                    this.setData(message);
                    this.eventReceived();
                }
            };
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clReadyToStart.countDown();
            clReadyToFinish.await();
            return user.getData();
        };

        Callable<String> senderActions = () -> {
            TestUser user = new TestUser(userName);
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clReadyToStart.await();
            chat.sendMessage(user, viMessage);
            clUserReceivedEvent.await();
            clReadyToFinish.countDown();
            return viMessage;
        };

        this.submit(senderActions, receiverActions);

        this.checkTasks("Message received in chat is not correct", viMessage);
    }
}
