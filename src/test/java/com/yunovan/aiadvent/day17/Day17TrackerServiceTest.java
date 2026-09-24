package com.yunovan.aiadvent.day17;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day17TrackerServiceTest {

    @TempDir
    Path tempDir;

    private Day17TrackerService service;

    @BeforeEach
    void setUp() {
        service = new Day17TrackerService(new Day17TicketStore(tempDir));
    }

    @Test
    void createTaskReturnsTicketWithIdAndStatus() {
        Day17Ticket ticket = service.createTask("Привезти стол", "для офиса", "Мария");

        assertThat(ticket.id()).startsWith("t-");
        assertThat(ticket.status()).isEqualTo("new");
        assertThat(ticket.title()).isEqualTo("Привезти стол");
        assertThat(ticket.description()).isEqualTo("для офиса");
        assertThat(ticket.assignee()).isEqualTo("Мария");
        assertThat(ticket.createdAt()).isNotBlank();
    }

    @Test
    void createTaskTrimsValues() {
        Day17Ticket ticket = service.createTask("  Купить кресло  ", null, "  ");

        assertThat(ticket.title()).isEqualTo("Купить кресло");
        assertThat(ticket.description()).isNull();
        assertThat(ticket.assignee()).isNull();
    }

    @Test
    void createTaskRequiresTitle() {
        assertThatThrownBy(() -> service.createTask("  ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("заголовок");
    }

    @Test
    void listTasksReturnsEmptyInitially() {
        assertThat(service.listTasks(null)).isEmpty();
    }

    @Test
    void listTasksFiltersByStatus() {
        service.createTask("Задача 1", null, null);

        List<Day17Ticket> all = service.listTasks("all");
        List<Day17Ticket> newTasks = service.listTasks("new");
        List<Day17Ticket> doneTasks = service.listTasks("done");

        assertThat(all).hasSize(1);
        assertThat(newTasks).hasSize(1);
        assertThat(doneTasks).isEmpty();
    }

    @Test
    void addCommentPersistsCommentToStore() {
        Day17Ticket ticket = service.createTask("Привезти стол", null, null);

        Day17Comment comment = service.addComment(ticket.id(), "проверил, всё ок");

        assertThat(comment.id()).startsWith("c-");
        assertThat(comment.taskId()).isEqualTo(ticket.id());
        assertThat(comment.author()).isEqualTo("ai-advent");
        assertThat(comment.text()).isEqualTo("проверил, всё ок");
        assertThat(comment.createdAt()).isNotBlank();
        assertThat(new Day17TicketStore(tempDir).comments(ticket.id())).hasSize(1);
    }

    @Test
    void addCommentToUnknownTaskThrows() {
        assertThatThrownBy(() -> service.addComment("t-unknown", "текст"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найдена");
    }

    @Test
    void addCommentRequiresTaskIdAndText() {
        Day17Ticket ticket = service.createTask("Задача", null, null);

        assertThatThrownBy(() -> service.addComment("", "текст"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId");
        assertThatThrownBy(() -> service.addComment(ticket.id(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("текст");
    }
}