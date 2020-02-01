package es.sidelab.webchat;

import es.codeurjc.webchat.Chat;
import es.codeurjc.webchat.ChatManager;
import es.codeurjc.webchat.User;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class ConcurrentTests {

    private static int getIdentifier() {
        return (int) Thread.currentThread().getId();
    }

    @Test
    public void concurrentUsersInChat() {
        final int numUsers = 4;
        final int numIterations = 5;
        final ChatManager manager = new ChatManager(50);

        Callable<Boolean> concurrentUserActions = () -> {
            TestUser user = new TestUser("user-"+ ConcurrentTests.getIdentifier());
            manager.newUser(user);

            for(int i = 0; i < numIterations; i++) {
                Chat chat = manager.newChat("chat"+i, 5, TimeUnit.SECONDS);
                chat.addUser(user);
                System.out.println(user+" users in "+ chat.getName() +": "+chat.getUsers().stream().map(User::getName).collect(Collectors.toList()));
            }

            return true;
        };

        ExecutorService executor = Executors.newFixedThreadPool(numUsers);
        CompletionService<Boolean> service = new ExecutorCompletionService<>(executor);

        for(int i = 0; i < numUsers; i++) {
            service.submit(concurrentUserActions);
        }

        for(int i = 0; i < numUsers; i++) {
            try {
                Future<Boolean> f = service.take();
                assertTrue("Task failed", f.get().booleanValue());
            }
            catch (ExecutionException ee) {
                throw new ConcurrentModificationException(ee.getCause());
            }
            catch (InterruptedException ie) {
                System.err.println("Another error has happened");
            }
        }
    }

    @Test
    public void notificationsInParallel() throws InterruptedException, TimeoutException {
        final int numReceivers = 3;
        final String chatName = "TestChat";
        ChatManager manager = new ChatManager(2);
        CountDownLatch clStart = new CountDownLatch(numReceivers);
        CountDownLatch cl = new CountDownLatch(numReceivers);
        CountDownLatch clFinish = new CountDownLatch(1);

        Callable<Boolean> receiverActions = () -> {
            TestUser user = new TestUser("user-"+ ConcurrentTests.getIdentifier()) {
                @Override
                public void newMessage(Chat chat, User user, String message) {
                    System.out.println("New message '" + message + "' from user " + user.getName()
                            + " in chat " + chat.getName());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        System.out.println("Error sleeping!!");
                    }
                    cl.countDown();
                }
            };
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clStart.countDown();
            clFinish.await();
            return true;
        };

        ExecutorService executor = Executors.newFixedThreadPool(numReceivers);
        CompletionService<Boolean> service = new ExecutorCompletionService<>(executor);

        for(int i = 0; i < numReceivers; i++) {
            service.submit(receiverActions);
        }

        TestUser user = new TestUser("sender");
        manager.newUser(user);
        Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
        chat.addUser(user);
        Instant start = Instant.now();
        clStart.await();
        chat.sendMessage(user, "The message!");
        cl.await();
        clFinish.countDown();
        Instant end = Instant.now();
        Duration elapsed = Duration.between(start, end);

        for(int i = 0; i < numReceivers; i++) {
            try {
                Future<Boolean> f = service.take();
                assertTrue("Task failed", f.get().booleanValue());
            }
            catch (ExecutionException ee) {
                throw new ConcurrentModificationException(ee.getCause());
            }
        }

        System.out.println("The time spent since the send and all received was "+elapsed.getSeconds()+"s "+elapsed.getNano()+"ns");

        assertTrue("Too long duration, is it sequential? ", elapsed.getSeconds() < (numReceivers));
    }

    @Test
    public void messageOrder() throws TimeoutException, InterruptedException {
        final String chatName = "OrderTestChat";
        final int numMessages = 5;
        ChatManager manager = new ChatManager(2);
        CountDownLatch clStart = new CountDownLatch(1);


        Callable<List<String>> receiverActions = () -> {
            List<String> messages = new ArrayList<>();
            CountDownLatch cl = new CountDownLatch(numMessages);
            TestUser user = new TestUser("receiver") {
                @Override
                public void newMessage(Chat chat, User user, String message) {
                    System.out.println("Receiver: '" + message + "' from user " + user.getName()
                            + " in chat " + chat.getName());
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        System.out.println("Error sleeping!!");
                    }
                    messages.add(message);
                    cl.countDown();
                }
            };
            manager.newUser(user);
            Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
            chat.addUser(user);
            clStart.countDown();
            cl.await();
            return messages;
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletionService<List<String>> service = new ExecutorCompletionService<>(executor);

        service.submit(receiverActions);

        //Now the sender actions
        TestUser user = new TestUser("sender");
        manager.newUser(user);
        Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
        chat.addUser(user);
        clStart.await();
        for (int i = 1; i <= numMessages; i++) {
            chat.sendMessage(user, Integer.toString(i));
        }

        try {
            Future<List<String>> f = service.take();
            //Assert the list is in correct order
            List<String> receivedMessages = f.get();
            for (int i = 1; i <= numMessages; i++) {
                assertTrue("Messsage in wrong order", Integer.parseInt(receivedMessages.get(i-1)) == i);
            }
        } catch (ExecutionException ee) {
            throw new ConcurrentModificationException(ee.getCause());
        }
    }
}
