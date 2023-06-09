package it.unitn.ds1;
import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Props;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.lang.Thread;
import java.lang.InterruptedException;


// The bank branch actor
public class Bank extends AbstractActor {
  private int id;                                     // bank ID
  private int balance = 1000;                         // balance
  private List<ActorRef> peers = new ArrayList<>();   // list of peer banks
  private int snapId = 0;                             // current snapshot ID
  private Random rnd = new Random();

  private boolean snapshotInitiator = false;    // the node is a snapshot initiator

  private HashMap<Integer,Snapshot> snapshots=new HashMap<>();

  /*-- Actor constructors --------------------------------------------------- */
  public Bank(int id, boolean snapshotInitiator) {
    this.id = id;
    this.snapshotInitiator = snapshotInitiator;
  }

  static public Props props(int id, boolean snapshotInitiator) {
    return Props.create(Bank.class, () -> new Bank(id, snapshotInitiator));
  }

  /*-- Message classes ------------------------------------------------------ */

  // Start message that informs every participant about its peers
  public static class JoinGroupMsg implements Serializable {
    public final List<ActorRef> group;   // an array of group members
    public JoinGroupMsg(List<ActorRef> group) {
      this.group = Collections.unmodifiableList(new ArrayList<ActorRef>(group));
    }
  }
  // Money transfer message
  public static class Money implements Serializable {
    public final int amount;
    public Money(int amount) {
      this.amount = amount;
    }
  }
  // Token (snapshot marker)
  public static class Token implements Serializable {
    public final int snapId;
    public Token(int snapId) {
      this.snapId = snapId;
    }
  }
  // Start snapshot request message
  public static class StartSnapshot implements Serializable {}
  // A message to self to schedule the next transaction
  public static class NextTransfer implements Serializable {}

  /*-- Actor logic ---------------------------------------------------------- */

  @Override
  public void preStart() {
    if(this.snapshotInitiator) {
      Cancellable timer = getContext().system().scheduler().scheduleWithFixedDelay(
              Duration.create(4, TimeUnit.SECONDS),        // when to start generating messages
              Duration.create(1, TimeUnit.SECONDS),        // how frequently generate them
              getSelf(),                                          // destination actor reference
              new StartSnapshot(),                                // the message to send
              getContext().system().dispatcher(),                 // system dispatcher
              getSelf()                                           // source of the message (myself)
      );
    }
  }

  // make a random money transfer
  private void randomTransfer() {
    int to = rnd.nextInt(this.peers.size());
    int amount = 1;
    balance -= amount;    // withdraw money from local account

    // model a random network/processing delay
    try { Thread.sleep(rnd.nextInt(10)); } 
    catch (InterruptedException e) { e.printStackTrace(); }
    peers.get(to).tell(new Money(amount), getSelf());
  }

  // send tokens to all the peers
  private void sendTokens(int snapId) {
    Token t = new Token(snapId);
    for (ActorRef p: peers) {
      // System.out.println("Bank " + id + " sending token to" + p);
      p.tell(t, getSelf());
    }
  }

  // capture the current state of the bank
  private void captureState(int snapid) {
    Snapshot s=new Snapshot();
    s.capturedBalance=this.balance;
    if(!this.snapshotInitiator)
      s.tokensReceived.add(getSender());
    this.snapshots.put(snapid,s);
    // TODO 1: save current balance and enter snapshot mode
    // TODO note: print your state only after the end of the snapshot protocol for this node
    //System.out.println("Bank " + id + " snapId: "+ snapId + " state: " + balance);
  }

  private void onJoinGroupMsg(JoinGroupMsg msg) {
    for (ActorRef b: msg.group) {
      if (!b.equals(getSelf())) { // copy all bank refs except for self
        this.peers.add(b);
      }
    }
    System.out.println("" + id + ": starting with " + 
        msg.group.size() + " peer(s)");
    getSelf().tell(new NextTransfer(), getSelf());  // schedule 1st transaction 
  }

  private void onNextTransfer(NextTransfer msg) {
    randomTransfer();
    getSelf().tell(new NextTransfer(), getSelf());  // schedule next transaction 
  }

  private void onMoney(Money msg) {
    balance += msg.amount;

    // TODO 2: implement logic for Money messages during snapshot
    for(Snapshot s: this.snapshots.values()){
        if(!s.tokensReceived.contains(getSender())){
          s.moneyInTransit+=msg.amount;
        }
    }
  }

  private void onToken(Token token) {
    // TODO 3: manage the first Token reception and the snapshot termination for this node
    if(!this.snapshots.containsKey(token.snapId)){
      captureState(token.snapId);
      sendTokens(token.snapId);
    }else{
      // add token sender to tokensReceived
      Snapshot s=this.snapshots.get(token.snapId);
      if(!s.tokensReceived.contains(getSender())) {
        s.tokensReceived.add(getSender());
        if (s.tokensReceived.size() == this.peers.size()) {
          System.out.println("Bank " + id + " snapId: " + token.snapId +
                  " state: " + balance + " recorded :" + s.capturedBalance +
                  " flowing : " + s.moneyInTransit);
          this.snapshots.remove(token.snapId);
        }
      }
    }
  }

  private void onStartSnapshot(StartSnapshot msg) {
    // we've been asked to initiate a snapshot
    // System.out.println("Bank " + id + " starting snapshot");
    snapId += 1;
    if(snapId<10) {
      captureState(snapId);
      sendTokens(snapId);
    }
  }

  // Here we define the mapping between the received message types
  // and our actor methods
  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(JoinGroupMsg.class,  this::onJoinGroupMsg)
      .match(NextTransfer.class,  this::onNextTransfer)
      .match(Money.class,         this::onMoney)
      .match(Token.class,         this::onToken)
      .match(StartSnapshot.class, this::onStartSnapshot)
      .build();
  }
}
