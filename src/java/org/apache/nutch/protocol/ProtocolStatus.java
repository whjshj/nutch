/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nutch.protocol;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;

import org.apache.hadoop.io.VersionMismatchException;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableUtils;

/**
 * @author Andrzej Bialecki
 */
public class ProtocolStatus implements Writable {

  private final static byte VERSION = 2;

  /** Content was retrieved without errors. */
  public static final int SUCCESS = 1;
  /** Content was not retrieved. Any further errors may be indicated in args. */
  public static final int FAILED = 2;

  /** This protocol was not found. Application may attempt to retry later. */
  public static final int PROTO_NOT_FOUND = 10;
  /** Resource is gone. */
  public static final int GONE = 11;
  /** Resource has moved permanently. New url should be found in args. */
  public static final int MOVED = 12;
  /** Resource has moved temporarily. New url should be found in args. */
  public static final int TEMP_MOVED = 13;
  /** Resource was not found. */
  public static final int NOTFOUND = 14;
  /** Temporary failure. Application may retry immediately. */
  public static final int RETRY = 15;
  /**
   * Unspecified exception occurred. Further information may be provided in args.
   */
  public static final int EXCEPTION = 16;
  /** Access denied - authorization required, but missing/incorrect. */
  public static final int ACCESS_DENIED = 17;
  /** Access denied by robots.txt rules. */
  public static final int ROBOTS_DENIED = 18;
  /** Too many redirects. */
  public static final int REDIR_EXCEEDED = 19;
  /** Not fetching. */
  public static final int NOTFETCHING = 20;
  /** Unchanged since the last fetch. */
  public static final int NOTMODIFIED = 21;
  public static final int IP_FOLD = 25;
  /**
   * Request was refused by protocol plugins, because it would block. The
   * expected number of milliseconds to wait before retry may be provided in
   * args.
   */
  @Deprecated
  public static final int WOULDBLOCK = 22;
  /** Thread was blocked http.max.delays times during fetching. */
  @Deprecated
  public static final int BLOCKED = 23;
  public static final int RUNTIME_ERROR= 26;

  public static final int SEVERE_ERROR = 27;

  public static final int CLIENT_ERROR = 28;
  // Useful static instances for status codes that don't usually require any
  // additional arguments.
  public static final ProtocolStatus STATUS_SUCCESS = new ProtocolStatus(
      SUCCESS);
  public static final ProtocolStatus STATUS_FAILED = new ProtocolStatus(FAILED);
  public static final ProtocolStatus STATUS_GONE = new ProtocolStatus(GONE);
  public static final ProtocolStatus STATUS_NOTFOUND = new ProtocolStatus(
      NOTFOUND);
  public static final ProtocolStatus STATUS_RETRY = new ProtocolStatus(RETRY);
  public static final ProtocolStatus STATUS_ROBOTS_DENIED = new ProtocolStatus(
      ROBOTS_DENIED);
  public static final ProtocolStatus STATUS_REDIR_EXCEEDED = new ProtocolStatus(
      REDIR_EXCEEDED);
  public static final ProtocolStatus STATUS_NOTFETCHING = new ProtocolStatus(
      NOTFETCHING);
  public static final ProtocolStatus STATUS_NOTMODIFIED = new ProtocolStatus(
      NOTMODIFIED);
  public static final ProtocolStatus STATUS_WOULDBLOCK = new ProtocolStatus(
      WOULDBLOCK);
  public static final ProtocolStatus STATUS_BLOCKED = new ProtocolStatus(
      BLOCKED);
  public static final ProtocolStatus STATUS_IP_FOLD = new ProtocolStatus(IP_FOLD);

  private int code;
  private long lastModified;
  private String[] args;

  private static final HashMap<Integer, String> codeToName = new HashMap<>();
  static {
    codeToName.put(Integer.valueOf(SUCCESS), "success");
    codeToName.put(Integer.valueOf(FAILED), "failed");
    codeToName.put(Integer.valueOf(PROTO_NOT_FOUND), "proto_not_found");
    codeToName.put(Integer.valueOf(GONE), "gone");
    codeToName.put(Integer.valueOf(MOVED), "moved");
    codeToName.put(Integer.valueOf(TEMP_MOVED), "temp_moved");
    codeToName.put(Integer.valueOf(NOTFOUND), "notfound");
    codeToName.put(Integer.valueOf(RETRY), "retry");
    codeToName.put(Integer.valueOf(EXCEPTION), "exception");
    codeToName.put(Integer.valueOf(ACCESS_DENIED), "access_denied");
    codeToName.put(Integer.valueOf(ROBOTS_DENIED), "robots_denied");
    codeToName.put(Integer.valueOf(REDIR_EXCEEDED), "redir_exceeded");
    codeToName.put(Integer.valueOf(NOTFETCHING), "notfetching");
    codeToName.put(Integer.valueOf(NOTMODIFIED), "notmodified");
    codeToName.put(Integer.valueOf(WOULDBLOCK), "wouldblock");
    codeToName.put(Integer.valueOf(BLOCKED), "blocked");
    codeToName.put(Integer.valueOf(RUNTIME_ERROR), "runtime_error");
    codeToName.put(Integer.valueOf(SEVERE_ERROR), "severe_error");
    codeToName.put(Integer.valueOf(CLIENT_ERROR), "client_error");
    codeToName.put(Integer.valueOf(IP_FOLD), "ip_fold");
  }

  public ProtocolStatus() {

  }

  public ProtocolStatus(int code, String[] args) {
    this.code = code;
    this.args = args;
  }

  public ProtocolStatus(int code, String[] args, long lastModified) {
    this.code = code;
    this.args = args;
    this.lastModified = lastModified;
  }

  public ProtocolStatus(int code) {
    this(code, null);
  }

  public ProtocolStatus(int code, long lastModified) {
    this(code, null, lastModified);
  }

  public ProtocolStatus(int code, Object message) {
    this(code, message, 0L);
  }

  public ProtocolStatus(int code, Object message, long lastModified) {
    this.code = code;
    this.lastModified = lastModified;
    if (message != null)
      this.args = new String[] { String.valueOf(message) };
  }
  public ProtocolStatus(Throwable t) {
    this(EXCEPTION, t);
  }

  public static ProtocolStatus read(DataInput in) throws IOException {
    ProtocolStatus res = new ProtocolStatus();
    res.readFields(in);
    return res;
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    byte version = in.readByte();
    switch (version) {
    case 1:
      code = in.readByte();
      lastModified = in.readLong();
      args = WritableUtils.readCompressedStringArray(in);
      break;
    case VERSION:
      code = in.readByte();
      lastModified = in.readLong();
      args = WritableUtils.readStringArray(in);
      break;
    default:
      throw new VersionMismatchException(VERSION, version);
    }
  }

  @Override
  public void write(DataOutput out) throws IOException {
    out.writeByte(VERSION);
    out.writeByte((byte) code);
    out.writeLong(lastModified);
    if (args == null) {
      out.writeInt(-1);
    } else {
      WritableUtils.writeStringArray(out, args);
    }
  }

  public void setArgs(String[] args) {
    this.args = args;
  }

  public String[] getArgs() {
    return args;
  }

  public int getCode() {
    return code;
  }

  public String getName() {
    return codeToName.get(this.code);
  }

  public void setCode(int code) {
    this.code = code;
  }

  public boolean isSuccess() {
    return code == SUCCESS;
  }

  public boolean isTransientFailure() {
    return code == ACCESS_DENIED || code == EXCEPTION || code == REDIR_EXCEEDED
        || code == RETRY || code == TEMP_MOVED || code == WOULDBLOCK
        || code == PROTO_NOT_FOUND;
  }

  public boolean isPermanentFailure() {
    return code == FAILED || code == GONE || code == MOVED || code == NOTFOUND
        || code == ROBOTS_DENIED;
  }

  public boolean isRedirect() {
      return code == MOVED || code == TEMP_MOVED;
  }

  public String getMessage() {
    if (args != null && args.length > 0)
      return args[0];
    return null;
  }

  public void setMessage(String msg) {
    if (args != null && args.length > 0)
      args[0] = msg;
    else
      args = new String[] { msg };
  }

  public long getLastModified() {
    return lastModified;
  }

  public void setLastModified(long lastModified) {
    this.lastModified = lastModified;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null)
      return false;
    if (!(o instanceof ProtocolStatus))
      return false;
    ProtocolStatus other = (ProtocolStatus) o;
    if (this.code != other.code || this.lastModified != other.lastModified)
      return false;
    if (this.args == null) {
      if (other.args == null)
        return true;
      else
        return false;
    } else {
      if (other.args == null)
        return false;
      if (other.args.length != this.args.length)
        return false;
      for (int i = 0; i < this.args.length; i++) {
        if (!this.args[i].equals(other.args[i]))
          return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuffer res = new StringBuffer();
    res.append(codeToName.get(Integer.valueOf(code)) + "(" + code
        + "), lastModified=" + lastModified);
    if (args != null) {
      if (args.length == 1) {
        res.append(": " + String.valueOf(args[0]));
      } else {
        for (int i = 0; i < args.length; i++) {
          if (args[i] != null)
            res.append(", args[" + i + "]=" + String.valueOf(args[i]));
        }
      }
    }
    return res.toString();
  }
}
