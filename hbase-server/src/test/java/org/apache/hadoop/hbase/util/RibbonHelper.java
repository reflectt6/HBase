package org.apache.hadoop.hbase.util;

public class RibbonHelper {
  public native void initRibbonFilter(int size);
  public native boolean addKey(String s);
  public native void backSubst();
  public native boolean filterQuery(String s);
  public native double getInitDuration();
  public native double getAddDuration();
  public native double getBackSubstDuration();
  public native double getQueryDuration();
  public native double getStringToCharsDuration();

  public native void close();
}
