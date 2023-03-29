package it.unitn.ds1;

import akka.actor.ActorRef;

import java.util.HashSet;
import java.util.Set;

public class Snapshot {
            // snapshot in progress
    public int capturedBalance = 0;              // captured state (balance)
    public int moneyInTransit = 0;               // "in-transit" messages (money)

    // set of peers we received a token from
    public Set<ActorRef> tokensReceived = new HashSet<>();
}
