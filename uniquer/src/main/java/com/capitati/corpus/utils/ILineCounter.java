package com.capitati.corpus.utils;

import java.util.Map;

public interface ILineCounter<T> {
  Map<T, Long> countLines() throws Exception;
}
