package es.sidelab.webchat;

import es.codeurjc.webchat.Chat;
import es.codeurjc.webchat.ChatManager;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.concurrent.*;

import static org.junit.Assert.assertTrue;

public class ChatCreationTimeoutTests {

    private class ExecutionResult {
        private Chat chat;
        private Duration elapsed;

        public ExecutionResult(Duration elapsed, Chat chat) {
            this.chat = chat;
            this.elapsed = elapsed;
        }

        public Chat getChat() {
            return chat;
        }

        public Duration getElapsed() {
            return elapsed;
        }
    }

    @Test
    public void chatCreationTimesOut() throws InterruptedException, ConcurrentModificationException {
        final int numChats = 10;
        final String chatPrefix = "ChatTimeout";
        final long timeout = 500; //milliseconds
        ChatManager manager = new ChatManager(numChats);

        Callable<ExecutionResult> creatorActions = () -> {
            Instant start = Instant.now();
            Chat chat = manager.newChat(chatPrefix+Thread.currentThread().getName(), timeout, TimeUnit.MILLISECONDS);
            Instant end = Instant.now();
            Duration elapsed = Duration.between(start, end);
            return new ExecutionResult(elapsed, chat);
        };

        ExecutorService executor = Executors.newFixedThreadPool(numChats+1);
        CompletionService<ExecutionResult> service = new ExecutorCompletionService<>(executor);

        for(int i = 0; i < numChats; i++) {
            service.submit(creatorActions);
        }

        for(int i = 0; i < numChats; i++) {
            try {
                Future<ExecutionResult> f = service.take();
                assertTrue("Chat can't be created", f.get().getChat() != null);
                assertTrue("Execution time too long", f.get().getElapsed().compareTo(Duration.ofMillis(timeout)) < 0);
            }
            catch (ExecutionException ee) {
                throw new ConcurrentModificationException(ee.getCause());
            }
        }

        Instant start = Instant.now();
        try {
            ExecutionResult resultOfTimeout = creatorActions.call();
            assertTrue("Chat was created", resultOfTimeout.getChat() == null);
            assertTrue("Execution time too short", resultOfTimeout.getElapsed().compareTo(Duration.ofMillis(timeout)) >= 0);
        }
        catch(TimeoutException te) {
            //It is correct
        }
        catch(Exception e) {
            assertTrue("Unhandled exception", false);
        }
        Instant end = Instant.now();
        Duration elapsed = Duration.between(start, end);
        assertTrue("Execution time too short", elapsed.compareTo(Duration.ofMillis(timeout)) >= 0);
    }

    @Test
    public void chatCreationAlmostTimesOut() throws InterruptedException, ConcurrentModificationException, TimeoutException {
        final int numChats = 10;
        final String chatPrefix = "ChatAlmostTimeout";
        final long timeout = 500; //milliseconds
        final long sleep = 300;
        ChatManager manager = new ChatManager(numChats);
        CountDownLatch clStart = new CountDownLatch(numChats);

        Callable<ExecutionResult> creatorActions = () -> {
            Instant start = Instant.now();
            Chat chat = manager.newChat(chatPrefix+Thread.currentThread().getName(), timeout, TimeUnit.MILLISECONDS);
            Instant end = Instant.now();
            Duration elapsed = Duration.between(start, end);
            clStart.countDown();
            return new ExecutionResult(elapsed, chat);
        };

        Callable<ExecutionResult> releaserActions = () -> {
            Instant start = Instant.now();
            Chat chat = manager.newChat(chatPrefix+Thread.currentThread().getName(), timeout, TimeUnit.MILLISECONDS);
            Instant end = Instant.now();
            Duration elapsed = Duration.between(start, end);
            clStart.countDown();
            Thread.sleep(sleep);
            manager.closeChat(chat);
            return new ExecutionResult(elapsed, chat);
        };

        ExecutorService executor = Executors.newFixedThreadPool(numChats+1);
        CompletionService<ExecutionResult> service = new ExecutorCompletionService<>(executor);

        for(int i = 0; i < numChats-1; i++) {
            service.submit(creatorActions);
        }
        service.submit(releaserActions);

        try {
            clStart.await();
            ExecutionResult resultOfTimeout = creatorActions.call();
            assertTrue("Chat was not created", resultOfTimeout.getChat() != null);
            assertTrue("Execution time too short", resultOfTimeout.getElapsed().compareTo(Duration.ofMillis(sleep)) >= 0);
            assertTrue("Execution time too long", resultOfTimeout.getElapsed().compareTo(Duration.ofMillis(timeout)) < 0);
        }
        catch(TimeoutException te) {
            throw te;
        }
        catch(Exception e) {
            assertTrue("Unhandled exception", false);
        }

        for(int i = 0; i < numChats; i++) {
            try {
                Future<ExecutionResult> f = service.take();
                assertTrue("Chat can't be created", f.get().getChat() != null);
                assertTrue("Execution time too long", f.get().getElapsed().compareTo(Duration.ofMillis(timeout)) < 0);
            }
            catch (ExecutionException ee) {
                throw new ConcurrentModificationException(ee.getCause());
            }
        }
    }
}
