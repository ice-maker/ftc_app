package org.firstinspires.ftc.teamcode.util.event;

import org.firstinspires.ftc.teamcode.util.Logger;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.Callable;

public class EventFlow
{
    private final EventBus bus;
    private HashMap<String, WeakReference<Node>> nodes; // nodes by name; hard references here only to make cleanup easier
    private Node rootNode;
    private Logger log = new Logger("Event Flow");
    
    public EventFlow(EventBus bus)
    {
        this.bus = bus;
    }
    
    public <T extends Event> NodeBuilder start(EventBus.Subscriber<T> rootSub)
    {
        if (!nodes.isEmpty()) throw new IllegalStateException("Event flow already built");
        if (rootSub.subbed())
        {
            log.w("Unsubscribing node");
            bus.unsubscribe(rootSub);
        }
        rootNode = new Node(rootSub);
        rootNode.subscribe();
        nodes.put(rootSub.name, new WeakReference<>(rootNode));
        return new NodeBuilder(rootNode);
    }
    
    public class NodeBuilder
    {
        private Node prevNode;
        
        protected NodeBuilder(Node prevNode)
        {
            this.prevNode = prevNode;
        }
        
        public NodeBuilder then(EventBus.Subscriber<?> sub)
        {
            if (sub.subbed())
            {
                log.w("Subscriber already subscribed; unsubscribing");
                bus.unsubscribe(sub);
            }
            Node nextNode = new Node(sub);
            nodes.put(sub.name, new WeakReference<>(nextNode));
            prevNode.next = nextNode;
            return new NodeBuilder(nextNode);
        }
    }
    
    private class Node
    {
        public final EventBus.Subscriber<?> sub;
        protected Node next;
        protected String name;
        
        protected <T extends Event> Node(EventBus.Subscriber<T> sub)
        {
            this.sub = new EventBus.Subscriber<>(sub.evClass,
                    (ev, bus1, sub1) -> {
                        sub.callback.run(ev, bus1, sub1);
                        unsubscribe();
                        if (next != null) next.subscribe();
                        else rootNode.subscribe();
                    }, sub.name, sub.channel);
            this.name = sub.name;
            next = null;
        }
        
        protected void subscribe()
        {
            bus.subscribe(sub);
        }
        
        protected void unsubscribe()
        {
            bus.unsubscribe(sub);
        }
        
        public Node getNext()
        {
            return next;
        }
    }
    
    @FunctionalInterface
    public interface BranchCondition
    {
        boolean branch();
    }
    
    // TODO: figure out syntax
    private class BranchNode extends Node
    {
        protected Node branch;
        protected BranchCondition condition;
        
        protected BranchNode(EventBus.Subscriber<?> sub, BranchCondition cond)
        {
            super(sub);
            this.condition = cond;
        }
        
        @Override
        public Node getNext()
        {
            if (condition.branch()) return branch;
            else return next;
        }
    }
}
