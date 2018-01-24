package org.batfish.datamodel.answers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import org.batfish.datamodel.Prefix;

public class AbstractReachabilityAnswerElement implements AnswerElement {
  public static class AbstractSliceAnswerElement implements AnswerElement {
    private static final String PROP_SLICE_ANSWER_ELEMENT = "sliceAnswerElement";
    private static final String PROP_DEST_PREFIXES = "destPrefixes";

    private List<Prefix> _destPrefixes;
    private AnswerElement _sliceAnswerElement;

    @JsonCreator
    public AbstractSliceAnswerElement() {}

    AbstractSliceAnswerElement(List<Prefix> destPrefixes, AnswerElement sliceAnswerElement) {
      _destPrefixes = destPrefixes;
      _sliceAnswerElement = sliceAnswerElement;
    }

    @JsonProperty(PROP_DEST_PREFIXES)
    public List<Prefix> getDestPrefixes() {
      return _destPrefixes;
    }

    @JsonProperty(PROP_DEST_PREFIXES)
    public void setDestPrefixes(List<Prefix> destPrefixes) {
      _destPrefixes = destPrefixes;
    }

    @JsonProperty(PROP_SLICE_ANSWER_ELEMENT)
    public AnswerElement getSliceAnswerElement() {
      return _sliceAnswerElement;
    }

    @JsonProperty(PROP_SLICE_ANSWER_ELEMENT)
    public void setSliceAnswerElement(AnswerElement sliceAnswerElement) {
      _sliceAnswerElement = sliceAnswerElement;
    }
  }

  private static final String PROP_ANSWERS = "answers";

  private List<AbstractSliceAnswerElement> _answers;

  public AbstractReachabilityAnswerElement() {
    _answers = new ArrayList<>();
  }

  public void addAnswer(List<Prefix> destPrefixes, AnswerElement sliceAnswerElement) {
    _answers.add(new AbstractSliceAnswerElement(destPrefixes, sliceAnswerElement));
  }

  @JsonProperty(PROP_ANSWERS)
  public List<AbstractSliceAnswerElement> getAnswers() {
    return _answers;
  }

  @JsonProperty(PROP_ANSWERS)
  public void setAnswers(List<AbstractSliceAnswerElement> answers) {
    _answers = answers;
  }
}
