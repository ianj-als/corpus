package com.capitati.corpus.utils;

import org.apache.commons.lang3.tuple.ImmutablePair;

public interface ICorpusSorter {
  ImmutablePair<Long, Long> sortWithUniquing() throws Exception;
}
