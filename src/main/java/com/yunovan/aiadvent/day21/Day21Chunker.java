package com.yunovan.aiadvent.day21;

import java.util.List;

public interface Day21Chunker {

    String strategy();

    List<Day21Chunk> chunk(Day21Document document);
}