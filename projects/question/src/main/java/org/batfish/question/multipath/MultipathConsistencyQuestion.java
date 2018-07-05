package org.batfish.question.multipath;

import org.batfish.datamodel.questions.Question;

/** A question to check for multipath inconsistencies. */
public class MultipathConsistencyQuestion extends Question {
  @Override
  public boolean getDataPlane() {
    return true;
  }

  @Override
  public String getName() {
    return "multipath";
  }
}
