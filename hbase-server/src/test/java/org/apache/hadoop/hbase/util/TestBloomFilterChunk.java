/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.nio.MultiByteBuff;
import org.apache.hadoop.hbase.regionserver.BloomType;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category({ MiscTests.class, SmallTests.class })
public class TestBloomFilterChunk {

  @ClassRule
  public static final HBaseClassTestRule CLASS_RULE =
    HBaseClassTestRule.forClass(TestBloomFilterChunk.class);

  @Test
  public void testBasicBloom() throws Exception {
    BloomFilterChunk bf1 = new BloomFilterChunk(1000, (float) 0.01, Hash.MURMUR_HASH, 0);
    BloomFilterChunk bf2 = new BloomFilterChunk(1000, (float) 0.01, Hash.MURMUR_HASH, 0);
    bf1.allocBloom();
    bf2.allocBloom();

    // test 1: verify no fundamental false negatives or positives
    byte[] key1 = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
    byte[] key2 = { 1, 2, 3, 4, 5, 6, 7, 8, 7 };

    bf1.add(key1, 0, key1.length);
    bf2.add(key2, 0, key2.length);

    assertTrue(BloomFilterUtil.contains(key1, 0, key1.length, new MultiByteBuff(bf1.bloom), 0,
      (int) bf1.byteSize, bf1.hash, bf1.hashCount));
    assertFalse(BloomFilterUtil.contains(key2, 0, key2.length, new MultiByteBuff(bf1.bloom), 0,
      (int) bf1.byteSize, bf1.hash, bf1.hashCount));
    assertFalse(BloomFilterUtil.contains(key1, 0, key1.length, new MultiByteBuff(bf2.bloom), 0,
      (int) bf2.byteSize, bf2.hash, bf2.hashCount));
    assertTrue(BloomFilterUtil.contains(key2, 0, key2.length, new MultiByteBuff(bf2.bloom), 0,
      (int) bf2.byteSize, bf2.hash, bf2.hashCount));

    byte[] bkey = { 1, 2, 3, 4 };
    byte[] bval = Bytes.toBytes("this is a much larger byte array");

    bf1.add(bkey, 0, bkey.length);
    bf1.add(bval, 1, bval.length - 1);

    assertTrue(BloomFilterUtil.contains(bkey, 0, bkey.length, new MultiByteBuff(bf1.bloom), 0,
      (int) bf1.byteSize, bf1.hash, bf1.hashCount));
    assertTrue(BloomFilterUtil.contains(bval, 1, bval.length - 1, new MultiByteBuff(bf1.bloom), 0,
      (int) bf1.byteSize, bf1.hash, bf1.hashCount));
    assertFalse(BloomFilterUtil.contains(bval, 0, bval.length, new MultiByteBuff(bf1.bloom), 0,
      (int) bf1.byteSize, bf1.hash, bf1.hashCount));

    // test 2: serialization & deserialization.
    // (convert bloom to byte array & read byte array back in as input)
    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
    bf1.writeBloom(new DataOutputStream(bOut));
    ByteBuffer bb = ByteBuffer.wrap(bOut.toByteArray());
    BloomFilterChunk newBf1 = new BloomFilterChunk(1000, (float) 0.01, Hash.MURMUR_HASH, 0);
    assertTrue(BloomFilterUtil.contains(key1, 0, key1.length, new MultiByteBuff(bb), 0,
      (int) newBf1.byteSize, newBf1.hash, newBf1.hashCount));
    assertFalse(BloomFilterUtil.contains(key2, 0, key2.length, new MultiByteBuff(bb), 0,
      (int) newBf1.byteSize, newBf1.hash, newBf1.hashCount));
    assertTrue(BloomFilterUtil.contains(bkey, 0, bkey.length, new MultiByteBuff(bb), 0,
      (int) newBf1.byteSize, newBf1.hash, newBf1.hashCount));
    assertTrue(BloomFilterUtil.contains(bval, 1, bval.length - 1, new MultiByteBuff(bb), 0,
      (int) newBf1.byteSize, newBf1.hash, newBf1.hashCount));
    assertFalse(BloomFilterUtil.contains(bval, 0, bval.length, new MultiByteBuff(bb), 0,
      (int) newBf1.byteSize, newBf1.hash, newBf1.hashCount));
    assertFalse(BloomFilterUtil.contains(bval, 0, bval.length, new MultiByteBuff(bb), 0,
      (int) newBf1.byteSize, newBf1.hash, newBf1.hashCount));

    System.out.println("Serialized as " + bOut.size() + " bytes");
    assertTrue(bOut.size() - bf1.byteSize < 10); // ... allow small padding
  }

  @Test
  public void testBloomFold() throws Exception {
    // test: foldFactor < log(max/actual)
    BloomFilterChunk b = new BloomFilterChunk(1003, (float) 0.01, Hash.MURMUR_HASH, 2);
    b.allocBloom();
    long origSize = b.getByteSize();
    assertEquals(1204, origSize);
    for (int i = 0; i < 12; ++i) {
      byte[] ib = Bytes.toBytes(i);
      b.add(ib, 0, ib.length);
    }
    b.compactBloom();
    assertEquals(origSize >> 2, b.getByteSize());
    int falsePositives = 0;
    for (int i = 0; i < 25; ++i) {
      byte[] bytes = Bytes.toBytes(i);
      if (
        BloomFilterUtil.contains(bytes, 0, bytes.length, new MultiByteBuff(b.bloom), 0,
          (int) b.byteSize, b.hash, b.hashCount)
      ) {
        if (i >= 12) falsePositives++;
      } else {
        assertFalse(i < 12);
      }
    }
    assertTrue(falsePositives <= 1);

    // test: foldFactor > log(max/actual)
  }

  @Test
  public void testBloomPerf() throws Exception {
    // add
    float err = (float) 0.01;
    BloomFilterChunk b = new BloomFilterChunk(10 * 1000 * 1000, (float) err, Hash.MURMUR_HASH, 3);
    b.allocBloom();
    long startTime = EnvironmentEdgeManager.currentTime();
    long origSize = b.getByteSize();
    for (int i = 0; i < 1 * 1000 * 1000; ++i) {
      byte[] ib = Bytes.toBytes(i);
      b.add(ib, 0, ib.length);
    }
    long endTime = EnvironmentEdgeManager.currentTime();
    System.out.println("Total Add time = " + (endTime - startTime) + "ms");

    // fold
    startTime = EnvironmentEdgeManager.currentTime();
    b.compactBloom();
    endTime = EnvironmentEdgeManager.currentTime();
    System.out.println("Total Fold time = " + (endTime - startTime) + "ms");
    assertTrue(origSize >= b.getByteSize() << 3);

    // test
    startTime = EnvironmentEdgeManager.currentTime();
    int falsePositives = 0;
    for (int i = 0; i < 2 * 1000 * 1000; ++i) {

      byte[] bytes = Bytes.toBytes(i);
      if (
        BloomFilterUtil.contains(bytes, 0, bytes.length, new MultiByteBuff(b.bloom), 0,
          (int) b.byteSize, b.hash, b.hashCount)
      ) {
        if (i >= 1 * 1000 * 1000) falsePositives++;
      } else {
        assertFalse(i < 1 * 1000 * 1000);
      }
    }
    endTime = EnvironmentEdgeManager.currentTime();
    System.out.println("Total Contains time = " + (endTime - startTime) + "ms");
    System.out.println("False Positive = " + falsePositives);
    assertTrue(falsePositives <= (1 * 1000 * 1000) * err);

    // test: foldFactor > log(max/actual)
  }

  @Test
  public void testSizing() {
    int bitSize = 8 * 128 * 1024; // 128 KB
    double errorRate = 0.025; // target false positive rate

    // How many keys can we store in a Bloom filter of this size maintaining
    // the given false positive rate, not taking into account that the n
    long maxKeys = BloomFilterUtil.idealMaxKeys(bitSize, errorRate);
    assertEquals(136570, maxKeys);

    // A reverse operation: how many bits would we need to store this many keys
    // and keep the same low false positive rate?
    long bitSize2 = BloomFilterUtil.computeBitSize(maxKeys, errorRate);

    // The bit size comes out a little different due to rounding.
    assertTrue(Math.abs(bitSize2 - bitSize) * 1.0 / bitSize < 1e-5);
  }

  @Test
  public void testFoldableByteSize() {
    assertEquals(128, BloomFilterUtil.computeFoldableByteSize(1000, 5));
    assertEquals(640, BloomFilterUtil.computeFoldableByteSize(5001, 4));
  }

  @Test
  public void testBloomChunkAndRibbon() throws Exception {
//    int byteSizeHint = 128 * 1024;
    int byteSizeHint = 155136;
    int fp_max_test = 100000;


    long start = System.currentTimeMillis();
    BloomFilterChunk bfc = BloomFilterUtil.createBySize(byteSizeHint, (float)0.0048, 1, 7, BloomType.ROW);
    bfc.allocBloom();
    long end = System.currentTimeMillis();
    long c1 = end - start;

    int keyNum = (int)bfc.getMaxKeys();
    System.out.println("maxKey = "+keyNum);
    System.out.println("filter size = " + byteSizeHint);
    System.out.println();

    byte[][] keys = new byte[146706 + fp_max_test][];
    String[] keys2 = new String[146706 + fp_max_test];
    for (int i = 0; i < 146706 + fp_max_test; i++) {
      keys[i] = Bytes.toBytes("this is a much larger byte array" + i * 0.618);
      keys2[i] = "this is a much larger byte array" + i * 0.618;
    }

    start = System.currentTimeMillis();
    // test for bloom
    for (int i = 0; i < keyNum; i++) {
      bfc.add(keys[i], 0, keys[i].length);
    }
    end = System.currentTimeMillis();
    System.out.println("bloom construct time = " + (end - start + c1));

    start = System.currentTimeMillis();
    for (int i = 0; i < keyNum; i++) {
      assertTrue(BloomFilterUtil.contains(keys[i], 0, keys[i].length, new MultiByteBuff(bfc.bloom), 0,
        (int) bfc.byteSize, bfc.hash, bfc.hashCount));
    }
    end = System.currentTimeMillis();
    System.out.println("bloom query added time = " + (end - start));

    int fpCount = 0;
    start = System.currentTimeMillis();
    for (int i = keys.length - 1; i >= keys.length - fp_max_test; i--) {
      if (BloomFilterUtil.contains(keys[i], 0, keys[i].length, new MultiByteBuff(bfc.bloom), 0,
        (int) bfc.byteSize, bfc.hash, bfc.hashCount) == true) {
        fpCount++;
      }
    }
    end = System.currentTimeMillis();
    System.out.println("bloom query not added time = " + (end - start));
    System.out.println("bloom fp rate = " + 0.1 * fpCount / keyNum);

    System.out.println();


    // test for ribbon
//    System.load("/Users/rainnight/codes/IdeaProjects/hbase/hbase-server/src/test/native/libribbon_filter.dylib");
    System.load("/Users/rainnight/codes/IdeaProjects/hbase/hbase-server/src/test/native/libribbon_filterV0.dylib");
    start = System.currentTimeMillis();
    RibbonHelper ribbonHelper = new RibbonHelper();

    ribbonHelper.initRibbonFilter(byteSizeHint);
    end = System.currentTimeMillis();
    long constructT = end - start;

    start = System.currentTimeMillis();
    boolean success = true;
    for (int i = 0; i < 146706; i++) {
      success &= ribbonHelper.addKey(keys2[i]);
    }
//    boolean success =ribbonHelper.addRangAndInit(byteSizeHint, keys2, 146706, false);
//    boolean success = ribbonHelper.addRang(keys2, 146706);
    end = System.currentTimeMillis();
    long constructT2 = end - start;
    System.out.println("banding result is " + success);

//    if (!success) {
//      throw new RuntimeException("not find seed");
//    }

    start = System.currentTimeMillis();
    ribbonHelper.backSubst();
    end = System.currentTimeMillis();
//    System.out.println("ribbon construct time =" + (end - start + constructT + constructT2));

//    System.out.println("ribbon add rang and init time =" + (constructT2));
//    System.out.println("ribbon backSubst time =" + (end - start));
//    System.out.println("ribbon construct time =" + (end - start + constructT2));

    System.out.println("ribbon init time =" + constructT);
    System.out.println("ribbon add range time =" + constructT2);
    System.out.println("ribbon backSubst time =" + (end - start));
    System.out.println("ribbon construct time =" + (end - start + constructT + constructT2));

    start = System.currentTimeMillis();
    int seriousError = 0;
    for (int i = 0; i < 146706; i++) {
//      assertTrue(ribbonHelper.filterQuery(keys2[i]));
      if (!ribbonHelper.filterQuery(keys2[i])) {
        seriousError++;
      }
//      System.out.println(i);
    }
    end = System.currentTimeMillis();
    System.out.println("ribbon query added time = " + (end - start));
    System.out.println("ribbon query added time serious error = " + seriousError + "/" + 146706);

    int fpCount2 = 0;
    start = System.currentTimeMillis();
    for (int i = keys.length - 1; i >= keys.length - fp_max_test; i--) {
      if (ribbonHelper.filterQuery(keys2[i])) {
        fpCount2++;
      }
    }
    end = System.currentTimeMillis();
    System.out.println("ribbon query not added time = " + (end - start));
    System.out.println("ribbon fp rate = " + 0.1 * fpCount2 / keyNum);
    System.out.println("////////////////////////////");
    System.out.println("ribbon native add time = " + ribbonHelper.getAddDuration());
    System.out.println("ribbon native backSubst time = " + ribbonHelper.getBackSubstDuration());
    System.out.println("ribbon native query time = " + ribbonHelper.getQueryDuration());
    System.out.println("ribbon native string to chars time = " + ribbonHelper.getStringToCharsDuration());
    System.out.println("ribbon native init time = " + ribbonHelper.getInitDuration());
    System.out.println("ribbon native string to hash64 time = " + ribbonHelper.getStringToHash64Duration());
    System.out.println("////////////////////////////");
    System.out.println("ribbon native construct time = " + (ribbonHelper.getInitDuration() + ribbonHelper.getAddDuration() + ribbonHelper.getBackSubstDuration()));
    ribbonHelper.close();
    System.out.println("////////////////////////////");
    System.out.println("ribbon space = 146706/155136");
    System.out.println("bloom space = " + keyNum + "/155136");
    System.out.println("improve space = " + (146706 + 0.0 - keyNum)/keyNum);
  }
}
