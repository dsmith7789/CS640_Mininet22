JFLAGS = -g
JC = javac -cp ./src $*.java
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java
CLASSES = \
	src/TCPSegment.java \
	src/Sender.java \
	src/Receiver.java \
	src/TCPend.java
default: classes
classes: $(CLASSES:.java=.class)
clean:
	$(RM) $*.class
