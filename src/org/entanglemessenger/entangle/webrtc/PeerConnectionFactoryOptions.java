package org.entanglemessenger.entangle.webrtc;


import org.webrtc.PeerConnectionFactory;

public class PeerConnectionFactoryOptions extends PeerConnectionFactory.Options {

  public PeerConnectionFactoryOptions() {
    this.networkIgnoreMask = 1 << 4;
  }
}
