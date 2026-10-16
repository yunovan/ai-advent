package com.yunovan.aiadvent.day35;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26MockOllama;
import com.yunovan.aiadvent.day26.Day26Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Day35MathTutorServiceTest {

    private Day26MockOllama mock;
    private Day35MathTutorService service;

    private Day35MathTutorService service(String endpoint) {
        Day26Properties props = new Day26Properties(endpoint, null, null, null, null, null);
        Day35Properties day35 = new Day35Properties(null, null, null, null, 3, null, null);
        return new Day35MathTutorService(day35, new Day35ProblemGenerator(),
                new Day35LessonRepository(), new Day35Tutor(new Day26LocalLlmClient(RestClient.builder(), props), day35));
    }

    @AfterEach
    void tearDown() {
        if (mock != null) {
            mock.close();
        }
    }

    @Test
    void startCreatesSessionWithRequestedProblemCount() {
        service = service(Day26MockOllama.closedEndpoint());

        Day35SessionResponse response = service.start(new Day35SessionRequest("algebra", "5-7", 3, null, null));

        assertThat(response.sessionId()).isNotBlank();
        assertThat(response.problems()).hasSize(3);
        assertThat(response.problem()).isNotNull();
        assertThat(response.progress().total()).isEqualTo(3);
        assertThat(response.finished()).isFalse();
    }

    @Test
    void answerMarksCorrectAndAdvancesToNextProblem() {
        mock = Day26MockOllama.start();
        service = service(mock.endpoint());

        Day35SessionResponse start = service.start(new Day35SessionRequest("algebra", "5-7", 2, null, null));
        Day35SessionResponse answered = service.answer(start.sessionId(),
                new Day35SessionRequest(null, null, null, "5", null));

        assertThat(answered.correct()).isTrue();
        assertThat(answered.solution().check()).isEqualTo("correct");
        assertThat(answered.progress().solved()).isEqualTo(1);
        assertThat(answered.progress().correct()).isEqualTo(1);
        assertThat(answered.finished()).isFalse();
        assertThat(answered.model()).isEqualTo(Day26Properties.DEFAULT_MODEL);
    }

    @Test
    void answerReportsWrongAnswerWithErrorAnalysis() {
        mock = Day26MockOllama.start();
        service = service(mock.endpoint());

        Day35SessionResponse start = service.start(new Day35SessionRequest("algebra", "5-7", 1, null, null));
        Day35SessionResponse answered = service.answer(start.sessionId(),
                new Day35SessionRequest(null, null, null, "999", null));

        assertThat(answered.correct()).isFalse();
        assertThat(answered.analysis()).isNotNull();
        assertThat(answered.analysis().mistake()).contains("999");
        assertThat(answered.finished()).isTrue();
    }

    @Test
    void answerFallsBackWhenModelUnavailable() {
        service = service(Day26MockOllama.closedEndpoint());

        Day35SessionResponse start = service.start(new Day35SessionRequest("fractions", "5-6", 1, null, null));
        Day35SessionResponse answered = service.answer(start.sessionId(),
                new Day35SessionRequest(null, null, null, "3/4", null));

        assertThat(answered.correct()).isTrue();
        assertThat(answered.solution().explanation()).contains("3/4");
        assertThat(answered.model()).isEmpty();
    }

    @Test
    void answerForUnknownSessionThrows() {
        service = service(Day26MockOllama.closedEndpoint());

        assertThatThrownBy(() -> service.answer("missing", new Day35SessionRequest(null, null, null, "5", null)))
                .isInstanceOf(Day35Exception.class)
                .hasMessageContaining("не найдена");
    }

    @Test
    void lessonsAreExposed() {
        service = service(Day26MockOllama.closedEndpoint());

        assertThat(service.lessons()).isNotEmpty();
    }
}
