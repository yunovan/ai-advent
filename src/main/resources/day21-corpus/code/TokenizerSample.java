package com.yunovan.samples;

/**
 * Обучающий образец токенизатора: русский текст без знаков препинания превращается
 * в нижнем регистре в последовательность слов. Используется как заготовка корпуса
 * для проверки фиксированной стратегии чанкинга.
 */
public final class TokenizerSample {

    private TokenizerSample() {
    }

    public static String[] tokenize(String text) {
        return text.toLowerCase()
                .replaceAll("[^\\p{L}\\p{Nd}\\s]", " ")
                .trim()
                .split("\\s+");
    }

    public static void main(String[] args) {
        String source = "Токенизация, эмбеддинги и поиск по индексу — это три шага RAG-пайплайна.";
        String[] words = tokenize(source);
        System.out.println("Слов в примере: " + words.length);
        for (String word : words) {
            System.out.println("- " + word);
        }
    }
}