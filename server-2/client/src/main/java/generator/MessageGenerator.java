package generator;

import model.ChatMessage;
import model.MessageRound;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageGenerator implements Runnable {
    private static final String[] MESSAGE_GROUP = {
            "Hello everyone!", "How are you doing?", "Great weather today!",
            "Anyone up for a chat?", "What's new?", "Good morning!",
            "Have a nice day!", "See you later!", "Thanks for sharing!",
            "That's interesting!", "I agree with that.", "Not sure about that.",
            "Can you elaborate?", "Makes sense to me.", "Good point!",
            "Let me think about it.", "I'll get back to you.", "Sounds good!",
            "Count me in!", "I'm not available.", "Maybe next time.",
            "Congratulations!", "Well done!", "Keep it up!",
            "Don't give up!", "You can do it!", "Great job!",
            "I'm here if you need.", "Let me know.", "Sure thing!",
            "No problem!", "My pleasure!", "Anytime!",
            "Absolutely!", "Definitely!", "For sure!",
            "I'm on my way.", "Be right back.", "One moment please.",
            "Just a second.", "Hold on.", "Almost there!",
            "Got it!", "Understood!", "Roger that!",
            "Copy that!", "Noted!", "Will do!",
            "On it!", "Consider it done!", "Leave it to me!"
    };

    private final BlockingQueue<MessageRound> roundQueue;
    private final int totalMessages;
    private final AtomicInteger generatedRounds;
    private final AtomicInteger generatedMessages;
    private final Random random;
    private final int NUM_OF_ROOMS = 20;

    public MessageGenerator(BlockingQueue<MessageRound> roundQueue, int totalMessages) {
        this.roundQueue = roundQueue;
        this.totalMessages = totalMessages;
        this.generatedRounds = new AtomicInteger(0);
        this.generatedMessages = new AtomicInteger(0);
        this.random = new Random();
    }

    @Override
    public void run() {
        System.out.println("Generating messages...");
        long startTime = System.currentTimeMillis();

        try {
            int messagesGenerated = 0;

            while (messagesGenerated < totalMessages) {
                MessageRound round = generateRound();
                roundQueue.put(round);

                messagesGenerated += round.getMessageCount();
                generatedRounds.incrementAndGet();
                generatedMessages.addAndGet(round.getMessageCount());
            }
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            System.out.println("Generated " + generatedRounds.get() + "rounds, " + "(" + generatedMessages.get() + "messages) in " + duration + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
            System.out.println("Message generation interrupted");
        }
    }

    private MessageRound generateRound() {
        int userId = random.nextInt(100000) + 1;
        String username = "user" + userId;
        String roomId = String.valueOf(random.nextInt(NUM_OF_ROOMS) + 1);

        MessageRound round = new MessageRound(roomId, String.valueOf(userId), username);

        ChatMessage joinMessage = new ChatMessage(
                username,
                String.valueOf(userId),
                "Joined the room",
                "JOIN"
        );
        round.addMessage(joinMessage);

        int numTextMessages = random.nextInt(10) + 1;
        for (int i = 0; i < numTextMessages; i++) {
            String messageText = MESSAGE_GROUP[random.nextInt(MESSAGE_GROUP.length)];
            ChatMessage textMessage = new ChatMessage(
                    username,
                    String.valueOf(userId),
                    messageText,
                    "TEXT"
            );
            round.addMessage(textMessage);
        }

        ChatMessage leaveMessage = new ChatMessage(
                username,
                String.valueOf(userId),
                "Left the room",
                "LEAVE"
        );
        round.addMessage(leaveMessage);

        return round;
    }

    public int getGeneratedRounds() {
        return generatedRounds.get();
    }

    public int getGeneratedMessages() {
        return generatedMessages.get();
    }
}
