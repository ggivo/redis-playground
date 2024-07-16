package com.redis.examples.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Maintain Hash Slot to Consumers map
 * <p>
 * Each slot will be served by at least {@code replicaCount} consumers.
 * Consumers serving the same slot use explicit locking based on the message ID to ensure each message is processed only once.
 * This guarantees that a message will be processed by only one consumer while allowing horizontal scaling.
 * Consequently, there will be exactly {@code replicaCount} consumers attempting to acquire a lease for processing a given message.
 */
@Service
public class HashSlotManager {
    private static final Logger logger = LoggerFactory.getLogger(HashSlotManager.class);

    @Autowired
    ObjectMapper objectMapper;

    private static class SlotMap {
        private final int totalSlots;
        private final int replicaCount;

        private SortedSet<String> activeSubscribers = new TreeSet<>();

        private final Map<Integer, Set<String>> slotToSubscribersMap = new HashMap<>();

        public SlotMap(int totalSlots, int replicaCount, SortedSet<String> activeSubscribers) {
            this.totalSlots = totalSlots;
            this.replicaCount = replicaCount;
            this.activeSubscribers = activeSubscribers;
            assignSubscribersToSlots();
        }

        public SortedSet<String> getActiveSubscribers() {
            return activeSubscribers;
        }

        public Map<Integer, Set<String>> getSlotToSubscribersMap() {
            return slotToSubscribersMap;
        }

        private void assignSubscribersToSlots() {
            int slot = 0;
            Iterator<String> subscriberIterator = activeSubscribers.iterator();

            // If there is at least one active subscriber
            if (subscriberIterator.hasNext()) {
                while (slot < totalSlots) {
                    Set<String> currentSubscribers = new HashSet<>();
                    // If there is at least one active subscriber
                    if (!activeSubscribers.isEmpty()) {
                        for (int i = 0; i < replicaCount; i++) {
                            if (!subscriberIterator.hasNext()) {
                                subscriberIterator = activeSubscribers.iterator(); // Wrap around if we reach end of subscribers set
                            }
                            currentSubscribers.add(subscriberIterator.next());
                        }
                    }
                    slotToSubscribersMap.put(slot++, currentSubscribers);
                }
            }
        }

        public Set<String> getSubscribers(String key) {
            int slot = getSlot(key);
            Set<String> subscribers = slotToSubscribersMap.get(slot);
            if (subscribers == null || subscribers.isEmpty()) {
                throw new IllegalStateException("No subscribers configured for slot: " + slot);
            }
            return subscribers;
        }

        public boolean isProcessedBy(String key, String subscriberId) {
            int slot = getSlot(key);
            Set<String> subscribers = slotToSubscribersMap.get(slot);
            return subscribers != null && subscribers.contains(subscriberId);
        }

        private int getSlot(String key) {
            return Math.abs(key.hashCode()) % totalSlots;
        }
    }

    private SlotMap currentSlotMap;

    @Autowired
    public HashSlotManager(@Value("${hashslot.slots.total}") int totalSlots, @Value("${hashslot.replica.count}") int replicaCount) {
        currentSlotMap = new SlotMap(totalSlots, replicaCount, new TreeSet<>());
    }

    public boolean isProcessedBy(String key, String subscriberId) {
        return currentSlotMap.isProcessedBy(key, subscriberId);
    }

    public void updateSlotMap(List<String> subscribers) {
        SortedSet<String> updated = new TreeSet<>(subscribers);
        if (currentSlotMap.getActiveSubscribers().equals(updated)) {
            return;
        }

        this.currentSlotMap = new SlotMap(currentSlotMap.totalSlots, currentSlotMap.replicaCount, updated);

        dumpSlotMap();
    }

    @EventListener(ActiveConsumersChangedEvent.class)
    public void activeConsumersChanged(ActiveConsumersChangedEvent e) {
        updateSlotMap(e.getNewConsumerIds());
    }

    private void dumpSlotMap() {
        try {
            logger.debug(objectMapper.writer().writeValueAsString(currentSlotMap.getActiveSubscribers()));
            logger.debug(objectMapper.writer().writeValueAsString(currentSlotMap.getSlotToSubscribersMap()));
        } catch (JsonProcessingException e) {
            logger.error("Error while dumping current slot map!", e);
        }
    }
}
