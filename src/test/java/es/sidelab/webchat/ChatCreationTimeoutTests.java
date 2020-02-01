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
    private final int numChats = 10;
    private final long timeout = 500; //milliseconds
    ChatManager manager = new ChatManager(numChats);
    private ExecutorService executor = Executors.newFixedThreadPool(numChats+1);
    private CompletionService<ExecutionResult> service = new ExecutorCompletionService<>(executor);

    private class ExecutionResult {
        private Chat chat;
        private Duration elapsed;

        public ExecutionResult(Duration elapsed, Chat chat) {
            this.chat = chat;
            this.elapsed = elapsed;
        }

        public ExecutionResult(Callable<Chat> toMeasure) throws Exception {
            Instant start = Instant.now();
            this.chat = toMeasure.call();
            Instant end = Instant.now();
            this.elapsed = Duration.between(start, end);
        }

        public Chat getChat() {
            return chat;
        }

        public Duration getElapsed() {
            return elapsed;
        }
    }

    @Test
    public void chatCreationTimesOut() throws Exception {
        final String chatPrefix = "ChatTimeout";

        Callable<ExecutionResult> creatorActions = () ->
            new ExecutionResult(()->
                   manager.newChat(chatPrefix+Thread.currentThread().getName(), timeout, TimeUnit.MILLISECONDS)
            );

        for(int i = 0; i < numChats; i++) {
            service.submit(creatorActions);
        }

        for(int i = 0; i < numChats; i++) {
            Future<ExecutionResult> f = service.take();
            assertTrue("Chat can't be created", f.get().getChat() != null);
            assertTrue("Execution time too long", f.get().getElapsed().compareTo(Duration.ofMillis(timeout)) < 0);
        }

        ExecutionResult assertion = new ExecutionResult(() -> {
            try {
                ExecutionResult resultOfTimeout = creatorActions.call();
                assertTrue("Chat was created", resultOfTimeout.getChat() == null);
                assertTrue("Execution time too short", resultOfTimeout.getElapsed().compareTo(Duration.ofMillis(timeout)) >= 0);
            }
            catch(TimeoutException te) {
                //It is expected
            }
            return null;
        });
        assertTrue("Execution time too short", assertion.getElapsed().compareTo(Duration.ofMillis(timeout)) >= 0);
    }

    @Test
    public void chatCreationAlmostTimesOut() throws Exception {
        final String chatPrefix = "ChatAlmostTimeout";
        final long sleep = 300;
        CountDownLatch clChatsCreated = new CountDownLatch(numChats);

        Callable<ExecutionResult> creatorActions = () -> {
            ExecutionResult result = new ExecutionResult(() ->
                manager.newChat(chatPrefix + Thread.currentThread().getName(), timeout, TimeUnit.MILLISECONDS));
            clChatsCreated.countDown();
            return result;
        };

        Callable<ExecutionResult> releaserActions = () -> {
            ExecutionResult result = new ExecutionResult(() ->
                    manager.newChat(chatPrefix+Thread.currentThread().getName(), timeout, TimeUnit.MILLISECONDS)
            );
            clChatsCreated.countDown();
            Thread.sleep(sleep);
            manager.closeChat(result.getChat());
            return result;
        };

        for(int i = 0; i < numChats-1; i++) {
            service.submit(creatorActions);
        }
        service.submit(releaserActions);


        clChatsCreated.await();
        ExecutionResult resultOfTimeout = creatorActions.call();
        assertTrue("Chat was not created", resultOfTimeout.getChat() != null);
        assertTrue("Execution time too short", resultOfTimeout.getElapsed().compareTo(Duration.ofMillis(sleep)) >= 0);
        assertTrue("Execution time too long", resultOfTimeout.getElapsed().compareTo(Duration.ofMillis(timeout)) < 0);


        for(int i = 0; i < numChats; i++) {
            Future<ExecutionResult> f = service.take();
            assertTrue("Chat can't be created", f.get().getChat() != null);
            assertTrue("Execution time too long", f.get().getElapsed().compareTo(Duration.ofMillis(timeout)) < 0);

        }
    }
}
