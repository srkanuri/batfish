package org.batfish.datamodel.questions.smt;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.batfish.datamodel.questions.InterfacesSpecifier;
import org.batfish.datamodel.questions.NodesSpecifier;

public class HeaderLocationQuestion extends HeaderQuestion {

  private static final boolean DEFAULT_NEGATE = false;

  private static final InterfacesSpecifier DEFAULT_FINAL_IFACE_REGEX = InterfacesSpecifier.ALL;

  private static final NodesSpecifier DEFAULT_FINAL_NODE_REGEX = NodesSpecifier.ALL;

  private static final NodesSpecifier DEFAULT_INGRESS_NODE_REGEX = NodesSpecifier.ALL;

  private static final NodesSpecifier DEFAULT_FAIL_NODE1_REGEX = NodesSpecifier.ALL;

  private static final NodesSpecifier DEFAULT_FAIL_NODE2_REGEX = NodesSpecifier.ALL;

  private static final NodesSpecifier DEFAULT_NOT_FINAL_NODE_REGEX = NodesSpecifier.NONE;

  private static final InterfacesSpecifier DEFAULT_NOT_FINAL_IFACE_REGEX = InterfacesSpecifier.NONE;

  private static final NodesSpecifier DEFAULT_NOT_INGRESS_NODE_REGEX = NodesSpecifier.NONE;

  private static final NodesSpecifier DEFAULT_NOT_FAIL_NODE1_REGEX = NodesSpecifier.NONE;

  private static final NodesSpecifier DEFAULT_NOT_FAIL_NODE2_REGEX = NodesSpecifier.NONE;

  private static final String PROP_NEGATE = "negate";

  private static final String PROP_FINAL_NODE_REGEX = "finalNodeRegex";

  private static final String PROP_FINAL_IFACE_REGEX = "finalIfaceRegex";

  private static final String PROP_INGRESS_NODE_REGEX = "ingressNodeRegex";

  private static final String FAIL_NODE1_REGEX_VAR = "failNode1Regex";

  private static final String FAIL_NODE2_REGEX_VAR = "failNode2Regex";

  private static final String PROP_NOT_FINAL_NODE_REGEX = "notFinalNodeRegex";

  private static final String PROP_NOT_FINAL_IFACE_REGEX = "notFinalIfaceRegex";

  private static final String PROP_NOT_INGRESS_NODE_REGEX = "notIngressNodeRegex";

  private static final String NOT_FAIL_NODE1_REGEX_VAR = "notFailNode1Regex";

  private static final String NOT_FAIL_NODE2_REGEX_VAR = "notFailNode2Regex";

  private boolean _negate;

  private NodesSpecifier _finalNodeRegex;

  private InterfacesSpecifier _finalIfaceRegex;

  private NodesSpecifier _ingressNodeRegex;

  private NodesSpecifier _failNode1Regex;

  private NodesSpecifier _failNode2Regex;

  private NodesSpecifier _notFinalNodeRegex;

  private InterfacesSpecifier _notFinalIfaceRegex;

  private NodesSpecifier _notIngressNodeRegex;

  private NodesSpecifier _notFailNode1Regex;

  private NodesSpecifier _notFailNode2Regex;

  public HeaderLocationQuestion() {
    super();
    _negate = DEFAULT_NEGATE;
    _finalNodeRegex = DEFAULT_FINAL_NODE_REGEX;
    _finalIfaceRegex = DEFAULT_FINAL_IFACE_REGEX;
    _ingressNodeRegex = DEFAULT_INGRESS_NODE_REGEX;
    _failNode1Regex = DEFAULT_FAIL_NODE1_REGEX;
    _failNode2Regex = DEFAULT_FAIL_NODE2_REGEX;
    _notFinalNodeRegex = DEFAULT_NOT_FINAL_NODE_REGEX;
    _notFinalIfaceRegex = DEFAULT_NOT_FINAL_IFACE_REGEX;
    _notIngressNodeRegex = DEFAULT_NOT_INGRESS_NODE_REGEX;
    _notFailNode1Regex = DEFAULT_NOT_FAIL_NODE1_REGEX;
    _notFailNode2Regex = DEFAULT_NOT_FAIL_NODE2_REGEX;
  }

  public HeaderLocationQuestion(HeaderLocationQuestion other) {
    super(other);
    this._negate = other._negate;
    this._finalNodeRegex = other._finalNodeRegex;
    this._finalIfaceRegex = other._finalIfaceRegex;
    this._ingressNodeRegex = other._ingressNodeRegex;
    this._failNode1Regex = other._failNode1Regex;
    this._failNode2Regex = other._failNode2Regex;
    this._notFinalNodeRegex = other._notFinalNodeRegex;
    this._notFinalIfaceRegex = other._notFinalIfaceRegex;
    this._notIngressNodeRegex = other._notIngressNodeRegex;
    this._notFailNode1Regex = other._notFailNode1Regex;
    this._notFailNode2Regex = other._notFailNode2Regex;
  }

  @JsonProperty(PROP_NEGATE)
  public boolean getNegate() {
    return _negate;
  }

  @JsonProperty(PROP_FINAL_NODE_REGEX)
  public NodesSpecifier getFinalNodeRegex() {
    return _finalNodeRegex;
  }

  @JsonProperty(PROP_FINAL_IFACE_REGEX)
  public InterfacesSpecifier getFinalIfaceRegex() {
    return _finalIfaceRegex;
  }

  @JsonProperty(PROP_INGRESS_NODE_REGEX)
  public NodesSpecifier getIngressNodeRegex() {
    return _ingressNodeRegex;
  }

  @JsonProperty(FAIL_NODE1_REGEX_VAR)
  public NodesSpecifier getFailNode1Regex() {
    return _failNode1Regex;
  }

  @JsonProperty(FAIL_NODE2_REGEX_VAR)
  public NodesSpecifier getFailNode2Regex() {
    return _failNode2Regex;
  }

  @JsonProperty(PROP_NOT_FINAL_NODE_REGEX)
  public NodesSpecifier getNotFinalNodeRegex() {
    return _notFinalNodeRegex;
  }

  @JsonProperty(PROP_NOT_FINAL_IFACE_REGEX)
  public InterfacesSpecifier getNotFinalIfaceRegex() {
    return _notFinalIfaceRegex;
  }

  @JsonProperty(PROP_NOT_INGRESS_NODE_REGEX)
  public NodesSpecifier getNotIngressNodeRegex() {
    return _notIngressNodeRegex;
  }

  @Override
  public String prettyPrint() {
    return String.format("headerlocation %s", prettyPrintParams());
  }

  @Override
  protected String prettyPrintParams() {
    try {
      String retString = super.prettyPrintParams();
      if (!(_negate == DEFAULT_NEGATE)) {
        retString += String.format(", %s=%s", PROP_NEGATE, _negate);
      }
      if (!_finalNodeRegex.equals(DEFAULT_FINAL_NODE_REGEX)) {
        retString += String.format(", %s=%s", PROP_FINAL_NODE_REGEX, _finalNodeRegex);
      }
      if (!_finalIfaceRegex.equals(DEFAULT_FINAL_IFACE_REGEX)) {
        retString += String.format(", %s=%s", PROP_FINAL_IFACE_REGEX, _finalIfaceRegex);
      }
      if (!_ingressNodeRegex.equals(DEFAULT_INGRESS_NODE_REGEX)) {
        retString += String.format(", %s=%s", PROP_INGRESS_NODE_REGEX, _ingressNodeRegex);
      }
      if (!_notFinalNodeRegex.equals(DEFAULT_NOT_FINAL_NODE_REGEX)) {
        retString += String.format(", %s=%s", PROP_NOT_FINAL_NODE_REGEX, _notFinalNodeRegex);
      }
      if (!_notFinalIfaceRegex.equals(DEFAULT_NOT_FINAL_IFACE_REGEX)) {
        retString += String.format(", %s=%s", PROP_NOT_FINAL_IFACE_REGEX, _notFinalIfaceRegex);
      }
      if (!_notIngressNodeRegex.equals(DEFAULT_NOT_INGRESS_NODE_REGEX)) {
        retString += String.format(", %s=%s", PROP_NOT_INGRESS_NODE_REGEX, _notIngressNodeRegex);
      }
      return retString;
    } catch (Exception e) {
      return "Pretty printing failed. Printing Json\n" + toJsonString();
    }
  }

  @JsonProperty(NOT_FAIL_NODE1_REGEX_VAR)
  public NodesSpecifier getNotFailNode1Regex() {
    return _notFailNode1Regex;
  }

  @JsonProperty(NOT_FAIL_NODE2_REGEX_VAR)
  public NodesSpecifier getNotFailNode2Regex() {
    return _notFailNode2Regex;
  }

  @JsonProperty(PROP_NEGATE)
  public void setNegate(boolean negate) {
    _negate = negate;
  }

  @JsonProperty(PROP_FINAL_NODE_REGEX)
  public void setFinalNodeRegex(NodesSpecifier regex) {
    _finalNodeRegex = regex;
  }

  @JsonProperty(PROP_FINAL_IFACE_REGEX)
  public void setFinalIfaceRegex(InterfacesSpecifier regex) {
    _finalIfaceRegex = regex;
  }

  @JsonProperty(PROP_INGRESS_NODE_REGEX)
  public void setIngressNodeRegex(NodesSpecifier regex) {
    _ingressNodeRegex = regex;
  }

  @JsonProperty(FAIL_NODE1_REGEX_VAR)
  public void setFailNode1Regex(NodesSpecifier regex) {
    _failNode1Regex = regex;
  }

  @JsonProperty(FAIL_NODE2_REGEX_VAR)
  public void setFailNode2Regex(NodesSpecifier regex) {
    _failNode2Regex = regex;
  }

  @JsonProperty(PROP_NOT_FINAL_NODE_REGEX)
  public void setNotFinalNodeRegex(NodesSpecifier notFinalNodeRegex) {
    _notFinalNodeRegex = notFinalNodeRegex;
  }

  @JsonProperty(PROP_NOT_FINAL_IFACE_REGEX)
  public void setNotFinalIfaceRegex(InterfacesSpecifier notFinalIfaceRegex) {
    _notFinalIfaceRegex = notFinalIfaceRegex;
  }

  @JsonProperty(PROP_NOT_INGRESS_NODE_REGEX)
  public void setNotIngressNodeRegex(NodesSpecifier notIngressNodeRegex) {
    _notIngressNodeRegex = notIngressNodeRegex;
  }

  @JsonProperty(NOT_FAIL_NODE1_REGEX_VAR)
  public void setNotFailNode1Regex(NodesSpecifier regex) {
    _notFailNode1Regex = regex;
  }

  @JsonProperty(NOT_FAIL_NODE2_REGEX_VAR)
  public void setNotFailNode2Regex(NodesSpecifier regex) {
    _notFailNode2Regex = regex;
  }
}
