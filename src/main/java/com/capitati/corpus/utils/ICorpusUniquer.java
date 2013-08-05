package com.capitati.corpus.utils;

import org.apache.commons.lang3.tuple.ImmutablePair;

public interface ICorpusUniquer {
  ImmutablePair<Long, Long> unique() throws Exception;
}
