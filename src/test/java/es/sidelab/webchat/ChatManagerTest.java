package es.sidelab.webchat;

import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.concurrent.*;

import org.junit.Ignore;
import org.junit.Test;

import es.codeurjc.webchat.Chat;
import es.codeurjc.webchat.ChatManager;
import es.codeurjc.webchat.User;

public class ChatManagerTest {

	@Ignore("It is not reliable in an asynchronous world")
	@Test
	public void newChat() throws InterruptedException, TimeoutException {

		// Crear el chat Manager
		ChatManager chatManager = new ChatManager(5);

		// Crear un usuario que guarda en chatName el nombre del nuevo chat
		final String[] chatName = new String[1];

		chatManager.newUser(new TestUser("user") {
			public void newChat(Chat chat) {
				chatName[0] = chat.getName();
			}
		});

		// Crear un nuevo chat en el chatManager
		chatManager.newChat("Chat", 5, TimeUnit.SECONDS);

		// Comprobar que el chat recibido en el método 'newChat' se llama 'Chat'
		assertTrue("The method 'newChat' should be invoked with 'Chat', but the value is "
				+ chatName[0], Objects.equals(chatName[0], "Chat"));
	}

	@Ignore("It is not reliable in an asynchronous world")
	@Test
	public void newUserInChat() throws InterruptedException, TimeoutException {

		ChatManager chatManager = new ChatManager(5);

		final String[] newUser = new String[1];

		TestUser user1 = new TestUser("user1") {
			@Override
			public void newUserInChat(Chat chat, User user) {
				newUser[0] = user.getName();
			}
		};

		TestUser user2 = new TestUser("user2");

		chatManager.newUser(user1);
		chatManager.newUser(user2);

		Chat chat = chatManager.newChat("Chat", 5, TimeUnit.SECONDS);

		chat.addUser(user1);
		chat.addUser(user2);

		assertTrue("Notified new user '" + newUser[0] + "' is not equal than user name 'user2'",
				"user2".equals(newUser[0]));

	}

	@Test
	public void concurrentUsersInChat() {
		final int numUsers = 4;
		final int numIterations = 5;
		final ChatManager manager = new ChatManager(50);

		Callable<Boolean> concurrentUserActions = () -> {
				TestUser user = new TestUser("user"+Thread.currentThread().getName());
				manager.newUser(user);

				for(int i = 0; i < numIterations; i++) {
					Chat chat = manager.newChat("chat"+i, 5, TimeUnit.SECONDS);
					chat.addUser(user);
					System.out.println("Users in chat: ");
					for(User u: chat.getUsers()) {
						System.out.print(u.getName()+" ");
					}
					System.out.println();
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
		final int numUsers = 4;
		final String chatName = "TestChat";
		ChatManager manager = new ChatManager(50);
		CountDownLatch cl = new CountDownLatch(numUsers-1);
		CountDownLatch clFinish = new CountDownLatch(1);

		Callable<Boolean> receiverActions = () -> {
			TestUser user = new TestUser("user "+Thread.currentThread().getName()) {
				@Override
				public void newMessage(Chat chat, User user, String message) {
					System.out.println("New message '" + message + "' from user " + user.getName()
							+ " in chat " + chat.getName());
					System.out.println("Slowing down a bit");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						System.out.println("Error sleeeping!!");
					}
					cl.countDown();
				}
			};
			manager.newUser(user);
			Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
			chat.addUser(user);
			clFinish.await();
			return true;
		};

		ExecutorService executor = Executors.newFixedThreadPool(numUsers);
		CompletionService<Boolean> service = new ExecutorCompletionService<>(executor);

		for(int i = 0; i < numUsers-1; i++) {
			service.submit(receiverActions);
		}

		TestUser user = new TestUser("user"+Thread.currentThread().getName());
		manager.newUser(user);
		Chat chat = manager.newChat(chatName, 5, TimeUnit.SECONDS);
		chat.addUser(user);
		Instant start = Instant.now();
		chat.sendMessage(user, "The message!");
		cl.await();
		clFinish.countDown();
		Instant end = Instant.now();
		Duration elapsed = Duration.between(start, end);

		for(int i = 0; i < numUsers-1; i++) {
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

		System.out.println("The time spent since the send and all received was "+elapsed.getSeconds()+"s "+elapsed.getNano()+"ns");

		assertTrue("Too much duration, is it sequential? ", elapsed.getSeconds() < (numUsers-1));
	}
}
