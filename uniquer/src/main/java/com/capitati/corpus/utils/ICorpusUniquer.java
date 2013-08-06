package com.capitati.corpus.utils;

import org.apache.commons.lang3.tuple.ImmutablePair;

public interface ICorpusUniquer {
  public static final int UNLIMITED_TOKENS = -1;

  ImmutablePair<Long, Long> unique(String suffix, int maxNoTokens)
  throws Exception;
}
