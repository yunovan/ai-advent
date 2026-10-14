package com.yunovan.aiadvent.day33;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day33CrmStoreTest {

    private static Day33Properties properties(String crmDir) {
        return new Day33Properties(null, null, null, null, "data/day33-none", crmDir,
                null, null, null, null, null, null);
    }

    private final Day33CrmStore store = new Day33CrmStore(properties("build/day33-none"));

    @Test
    void loadsBundledCrm() {
        assertThat(store.users()).hasSize(4);
        assertThat(store.tickets()).hasSize(5);
        assertThat(store.users()).extracting(Day33User::id)
                .contains("U-1001", "U-1002", "U-1003", "U-1004");
    }

    @Test
    void findsUserAndTicketById() {
        assertThat(store.user("U-1001")).isPresent()
                .get().extracting(Day33User::name).isEqualTo("Иван Петров");
        assertThat(store.ticket("T-1001")).isPresent()
                .get().extracting(Day33Ticket::subject).isEqualTo("Не работает авторизация");
    }

    @Test
    void normalizesShortIds() {
        assertThat(store.user("1001")).isPresent();
        assertThat(store.ticket("1001")).isPresent();
        assertThat(store.user("u-1001")).isPresent();
    }

    @Test
    void missingIdsReturnEmpty() {
        assertThat(store.user("U-9999")).isEmpty();
        assertThat(store.ticket("T-9999")).isEmpty();
    }

    @Test
    void userTicketsAreSortedAndComplete() {
        List<Day33Ticket> tickets = store.userTickets("U-1001");

        assertThat(tickets).hasSize(2);
        assertThat(tickets.getFirst().updatedAt().compareTo(tickets.getLast().updatedAt()))
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    void searchFindsTicketsByText() {
        assertThat(store.searchTickets("авторизация", 5))
                .extracting(Day33Ticket::id).contains("T-1001");
        assertThat(store.searchTickets("2fa", 5))
                .extracting(Day33Ticket::id).contains("T-1001", "T-1004");
        assertThat(store.searchTickets("биллинг", 5))
                .extracting(Day33Ticket::id).contains("T-1002");
        assertThat(store.searchTickets("несуществующая-тема", 5)).isEmpty();
    }

    @Test
    void searchRespectsLimit() {
        assertThat(store.searchTickets("2fa", 1)).hasSize(1);
    }

    @Test
    void openTicketsExcludeClosedAndRankByPriority() {
        List<Day33Ticket> open = store.openTickets(5);

        assertThat(open).extracting(Day33Ticket::id).doesNotContain("T-1002");
        assertThat(open.getFirst().priority()).isEqualTo("high");
    }

    @Test
    void describeUserAndTicketContainKeyFacts() {
        String user = store.describeUser(store.user("U-1001").orElseThrow());
        assertThat(user).contains("U-1001", "Иван Петров", "Business");

        String ticket = store.describeTicket(store.ticket("T-1001").orElseThrow());
        assertThat(ticket).contains("T-1001", "Не работает авторизация", "open", "U-1001");
    }

    @Test
    void readsExternalDirectoryWhenPresent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("users.json"),
                "[{\"id\":\"U-7001\",\"name\":\"Тест\",\"email\":\"t@e\",\"plan\":\"Pro\","
                        + "\"company\":\"X\",\"since\":\"2026-01-01\"}]", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("tickets.json"),
                "[{\"id\":\"T-7001\",\"userId\":\"U-7001\",\"subject\":\"Вопрос\",\"status\":\"open\","
                        + "\"priority\":\"low\",\"product\":\"Web\",\"createdAt\":\"2026-01-01T00:00:00\","
                        + "\"updatedAt\":\"2026-01-02T00:00:00\",\"tags\":[],\"messages\":[]}]",
                StandardCharsets.UTF_8);

        Day33CrmStore external = new Day33CrmStore(properties(dir.toString()));

        assertThat(external.users()).extracting(Day33User::id).containsExactly("U-7001");
        assertThat(external.tickets()).extracting(Day33Ticket::id).containsExactly("T-7001");
    }
}
