package com.yunovan.aiadvent.day33;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day33CrmStore {

    private static final Logger log = LoggerFactory.getLogger(Day33CrmStore.class);

    private static final String BUNDLED_USERS = "day33/crm/users.json";
    private static final String BUNDLED_TICKETS = "day33/crm/tickets.json";

    private static final List<String> PRIORITY_ORDER =
            List.of("critical", "high", "medium", "low");

    private final Day33Properties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object lock = new Object();

    private volatile Snapshot snapshot;

    public Day33CrmStore(Day33Properties properties) {
        this.properties = properties;
    }

    public Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            synchronized (lock) {
                current = snapshot;
                if (current == null) {
                    current = build();
                    snapshot = current;
                }
            }
        }
        return current;
    }

    public void reload() {
        synchronized (lock) {
            snapshot = null;
        }
    }

    public List<Day33User> users() {
        return snapshot().users();
    }

    public List<Day33Ticket> tickets() {
        return snapshot().tickets();
    }

    public Optional<Day33User> user(String userId) {
        String id = normalizeId(userId, "U-");
        return snapshot().users().stream()
                .filter(user -> user.id().equalsIgnoreCase(id))
                .findFirst();
    }

    public Optional<Day33Ticket> ticket(String ticketId) {
        String id = normalizeId(ticketId, "T-");
        return snapshot().tickets().stream()
                .filter(ticket -> ticket.id().equalsIgnoreCase(id))
                .findFirst();
    }

    public List<Day33Ticket> userTickets(String userId) {
        String id = normalizeId(userId, "U-");
        return snapshot().tickets().stream()
                .filter(ticket -> ticket.userId().equalsIgnoreCase(id))
                .sorted(Comparator.comparing(Day33Ticket::updatedAt).reversed())
                .toList();
    }

    public List<Day33Ticket> searchTickets(String query, int limit) {
        String[] terms = query == null ? new String[0]
                : query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        int cap = limit <= 0 ? 5 : limit;
        List<Day33Ticket> matched = new ArrayList<>();
        for (Day33Ticket ticket : snapshot().tickets()) {
            String haystack = haystack(ticket);
            boolean all = true;
            for (String term : terms) {
                if (!term.isBlank() && !haystack.contains(term)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                matched.add(ticket);
            }
        }
        matched.sort(Comparator.comparing(Day33Ticket::updatedAt).reversed());
        return matched.stream().limit(cap).toList();
    }

    public List<Day33Ticket> openTickets(int limit) {
        int cap = limit <= 0 ? 5 : limit;
        return snapshot().tickets().stream()
                .filter(ticket -> !"closed".equalsIgnoreCase(ticket.status())
                        && !"resolved".equalsIgnoreCase(ticket.status()))
                .sorted(Comparator
                        .comparingInt((Day33Ticket ticket) -> priorityRank(ticket.priority()))
                        .thenComparing(Day33Ticket::updatedAt, Comparator.reverseOrder()))
                .limit(cap)
                .toList();
    }

    public String describeUser(Day33User user) {
        StringBuilder text = new StringBuilder();
        text.append("Пользователь ").append(user.id()).append('\n');
        text.append("Имя: ").append(user.name()).append('\n');
        text.append("Email: ").append(user.email()).append('\n');
        text.append("Тариф: ").append(user.plan()).append('\n');
        text.append("Компания: ").append(user.company()).append('\n');
        text.append("Клиент с: ").append(user.since()).append('\n');
        List<Day33Ticket> tickets = userTickets(user.id());
        text.append("Обращений: ").append(tickets.size());
        if (!tickets.isEmpty()) {
            text.append(" (");
            text.append(tickets.stream()
                    .map(ticket -> ticket.id() + " " + ticket.status())
                    .reduce((left, right) -> left + ", " + right).orElse(""));
            text.append(')');
        }
        return text.toString().trim();
    }

    public String describeTicket(Day33Ticket ticket) {
        Optional<Day33User> user = user(ticket.userId());
        StringBuilder text = new StringBuilder();
        text.append("Обращение ").append(ticket.id()).append('\n');
        text.append("Тема: ").append(ticket.subject()).append('\n');
        text.append("Статус: ").append(ticket.status())
                .append(" | Приоритет: ").append(ticket.priority()).append('\n');
        text.append("Продукт: ").append(ticket.product()).append('\n');
        text.append("Пользователь: ").append(ticket.userId());
        user.ifPresent(value -> text.append(" (").append(value.name())
                .append(", тариф ").append(value.plan()).append(')'));
        text.append('\n');
        text.append("Создано: ").append(ticket.createdAt())
                .append(" | Обновлено: ").append(ticket.updatedAt()).append('\n');
        if (!ticket.tags().isEmpty()) {
            text.append("Теги: ").append(String.join(", ", ticket.tags())).append('\n');
        }
        if (!ticket.messages().isEmpty()) {
            text.append("Переписка:\n");
            for (Day33TicketMessage message : ticket.messages()) {
                text.append("- [").append(message.at()).append(", ").append(message.author())
                        .append(" (").append(message.role()).append(")]: ")
                        .append(message.text()).append('\n');
            }
        }
        return text.toString().trim();
    }

    public String describeTickets(String header, List<Day33Ticket> tickets) {
        if (tickets.isEmpty()) {
            return header + ": ничего не найдено.";
        }
        StringBuilder text = new StringBuilder(header).append(": ").append(tickets.size()).append('\n');
        for (Day33Ticket ticket : tickets) {
            text.append("- ").append(ticket.id())
                    .append(" [").append(ticket.status()).append('/').append(ticket.priority())
                    .append("] ").append(ticket.subject())
                    .append(" — пользователь ").append(ticket.userId())
                    .append(", обновлено ").append(ticket.updatedAt()).append('\n');
        }
        return text.toString().trim();
    }

    public String describeUsers() {
        StringBuilder text = new StringBuilder("Пользователи: ")
                .append(users().size()).append('\n');
        for (Day33User user : users()) {
            text.append("- ").append(user.id()).append(" ").append(user.name())
                    .append(" (").append(user.email()).append(", тариф ").append(user.plan())
                    .append(", обращений ").append(userTickets(user.id()).size()).append(")\n");
        }
        return text.toString().trim();
    }

    private Snapshot build() {
        List<Day33User> users = loadUsers();
        List<Day33Ticket> tickets = loadTickets();
        log.info("День 33: CRM загружена — {} пользователей, {} обращений",
                users.size(), tickets.size());
        return new Snapshot(List.copyOf(users), List.copyOf(tickets));
    }

    private List<Day33User> loadUsers() {
        JsonNode root = read("users.json", BUNDLED_USERS);
        List<Day33User> users = new ArrayList<>();
        if (root != null) {
            for (JsonNode node : root) {
                users.add(new Day33User(
                        text(node, "id"), text(node, "name"), text(node, "email"),
                        text(node, "plan"), text(node, "company"), text(node, "since")));
            }
        }
        return users;
    }

    private List<Day33Ticket> loadTickets() {
        JsonNode root = read("tickets.json", BUNDLED_TICKETS);
        List<Day33Ticket> tickets = new ArrayList<>();
        if (root != null) {
            for (JsonNode node : root) {
                List<String> tags = new ArrayList<>();
                for (JsonNode tag : node.path("tags")) {
                    tags.add(tag.asText());
                }
                List<Day33TicketMessage> messages = new ArrayList<>();
                for (JsonNode message : node.path("messages")) {
                    messages.add(new Day33TicketMessage(
                            text(message, "author"), text(message, "role"),
                            text(message, "at"), text(message, "text")));
                }
                tickets.add(new Day33Ticket(
                        text(node, "id"), text(node, "userId"), text(node, "subject"),
                        text(node, "status"), text(node, "priority"), text(node, "product"),
                        text(node, "createdAt"), text(node, "updatedAt"),
                        List.copyOf(tags), List.copyOf(messages)));
            }
        }
        return tickets;
    }

    private JsonNode read(String fileName, String bundled) {
        Path external = Path.of(properties.crmDir()).toAbsolutePath().normalize().resolve(fileName);
        try {
            if (Files.isRegularFile(external)) {
                return mapper.readTree(Files.readString(external, StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            log.warn("День 33: не удалось прочитать {}: {}", external, ex.getMessage());
        }
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(bundled)) {
            if (input != null) {
                return mapper.readTree(input);
            }
        } catch (IOException ex) {
            log.warn("День 33: не удалось прочитать встроенный ресурс {}: {}", bundled, ex.getMessage());
        }
        log.warn("День 33: данные CRM {} не найдены ни в {}, ни во встроенных ресурсах",
                fileName, properties.crmDir());
        return null;
    }

    private static String haystack(Day33Ticket ticket) {
        StringBuilder text = new StringBuilder();
        text.append(ticket.id()).append(' ').append(ticket.subject()).append(' ')
                .append(ticket.product()).append(' ').append(ticket.status()).append(' ')
                .append(ticket.priority()).append(' ').append(String.join(" ", ticket.tags()));
        for (Day33TicketMessage message : ticket.messages()) {
            text.append(' ').append(message.text());
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static int priorityRank(String priority) {
        int index = PRIORITY_ORDER.indexOf(priority == null ? "" : priority.toLowerCase(Locale.ROOT));
        return index < 0 ? PRIORITY_ORDER.size() : index;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String normalizeId(String id, String prefix) {
        String value = id == null ? "" : id.trim();
        if (value.isEmpty()) {
            return value;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
            return prefix + value.substring(prefix.length()).trim();
        }
        if (value.chars().allMatch(Character::isDigit)) {
            return prefix + value;
        }
        return value;
    }

    public record Snapshot(List<Day33User> users, List<Day33Ticket> tickets) {
    }
}
