import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Random;
import tester.*;
import javalib.impworld.*;
import java.awt.Color;
import javalib.worldimages.*;

// represents a vertex of a graph with coordinates and edges connecting to itself
class Vertex {
  Edge up;
  Edge left;
  Edge right;
  Edge down;
  int x;
  int y;
  ArrayList<Edge> outEdges;

  // constructor given only coordinates
  Vertex(int x, int y) {
    this(null, null, null, null, x, y);
  }

  // constructor with edges and coordinates
  Vertex(Edge up, Edge left, Edge right, Edge down, int x, int y) {
    this.up = up;
    this.left = left;
    this.right = right;
    this.down = down;
    this.x = x;
    this.y = y;
    this.outEdges = new ArrayList<>();
    this.updateVertex();
  }

  // updates the vertex's list of edges
  void updateVertex() {
    ArrayList<Edge> edgeTemp = new ArrayList<>(
        Arrays.asList(this.up, this.left, this.right, this.down));
    for (Edge e : edgeTemp) {
      if (e != null) {
        this.outEdges.add(e);
      }
    }
  }
}

// represents edges of graphs with vertices at both ends and a weight
class Edge {
  Vertex from;
  Vertex to;
  int weight;

  // constructor
  Edge(Vertex from, Vertex to, int weight) {
    this.from = from;
    this.to = to;
    this.weight = weight;
  }

  // draws this edge as a vertical line
  WorldImage drawVerticalEdge() {
    return new RectangleImage(1, 10, "solid", Color.black);
  }

  // draws this edge as a horizontal line
  WorldImage drawHorizontalEdge() {
    return new RectangleImage(10, 1, "solid", Color.black);
  }
}

// represents a maze
class Maze extends World {
  int width;
  int height;
  Random rand;
  ArrayList<Vertex> vertices;
  HashMap<Vertex, Vertex> representatives;
  ArrayList<Edge> worklist; // all edges in graph, sorted by edge weights;
  ArrayList<Edge> edgesInTree;
  Player player;
  ArrayList<Vertex> searchPath;
  ICollection<Vertex> searchWorkList;
  Deque<Vertex> alreadySeen;
  HashMap<Vertex, Vertex> cameFrom;
  ArrayList<Vertex> solution;
  boolean togglePath;
  int wrongMoves;
  int algoWrongMoves;

  // random world constructor
  Maze(int width, int height) {
    this(width, height, new Random());
  }

  // testing constructor
  Maze(int width, int height, Random rand) {
    this.width = width;
    this.height = height;
    this.rand = rand;
    this.vertices = this.makeVertices();
    this.representatives = this.makeRepresentatives();
    this.worklist = this.sortEdges(this.makeUnsortedEdges());
    this.edgesInTree = this.unionFindData();
    this.player = new Player(this.vertices.get(0), new ArrayList<>());
    this.searchPath = new ArrayList<>();
    this.searchWorkList = new Queue<>();
    this.alreadySeen = new ArrayDeque<Vertex>();
    this.cameFrom = new HashMap<Vertex, Vertex>();
    this.solution = this.makeSolution();
    this.togglePath = true;
    this.wrongMoves = 0;
    this.algoWrongMoves = 0;
  }

  // draws this maze world with an empty scene
  public WorldScene makeScene() {
    return this.makeScene(this.getEmptyScene());
  }

  // test method for makeScene, draws this maze world with the given scene
  public WorldScene makeScene(WorldScene scene) {
    // draws the search path
    for (Vertex v : this.searchPath) {
      scene.placeImageXY(new RectangleImage(10, 10, "solid", Color.cyan), v.x * 10 + 5,
          v.y * 10 + 5);
    }
    // draws the player's path
    if (this.togglePath) {
      for (Vertex v : this.player.path) {
        scene.placeImageXY(new RectangleImage(10, 10, "solid", new Color(32, 205, 32)),
            v.x * 10 + 5, v.y * 10 + 5);
      }
    }
    // goal
    scene.placeImageXY(new RectangleImage(10, 10, "solid", Color.pink), this.width * 10 - 5,
        this.height * 10 - 5);
    // player
    scene.placeImageXY(new RectangleImage(10, 10, "solid", Color.green),
        this.player.curr.x * 10 + 5, this.player.curr.y * 10 + 5);
    // draws the possible paths of this maze
//    for (Edge e : this.edgesInTree) {
//      if (e.from.x == e.to.x) {
//        scene.placeImageXY(e.drawVerticalEdge(), e.from.x * 10 + 5, e.from.y * 10 + 10);
//      }
//      else {
//        scene.placeImageXY(e.drawHorizontalEdge(), e.from.x * 10 + 10, e.from.y * 10 + 5);
//      }
//    }
    // draws the walls of this maze
    for (Vertex v : this.vertices) {
      for (Edge e : v.outEdges) {
        if (!this.edgesInTree.contains(e)) {
          if (e == v.left) {
            scene.placeImageXY(e.drawVerticalEdge(), v.x * 10, v.y * 10 + 5);
          }
          else if (e == v.up) {
            scene.placeImageXY(e.drawHorizontalEdge(), v.x * 10 + 5, v.y * 10);
          }
          else if (e == v.right) {
            scene.placeImageXY(e.drawVerticalEdge(), v.x * 10 + 10, v.y * 10 + 5);
          }
          else if (e == v.down) {
            scene.placeImageXY(e.drawHorizontalEdge(), v.x * 10 + 5, v.y * 10 + 10);
          }
        }
      }
    }
    scene.placeImageXY(
        (new RectangleImage(this.width * 10, this.height * 10, "outline", Color.black)),
        this.width * 10 / 2, this.height * 10 / 2);
    scene.placeImageXY(new TextImage("Wrong Moves: " + this.wrongMoves, 10, Color.black), 100,
        this.height * 10 + 15);
    scene.placeImageXY(
        new TextImage("Algorithm Wrong Moves: " + this.algoWrongMoves, 10, Color.black), 100,
        this.height * 10 + 30);
    return scene;
  }

  // draws this maze world with an empty scene
  public WorldScene lastScene(String msg) {
    WorldScene scene = this.makeScene();
    for (int i = 1; i < this.solution.size() - 1; i++) {
      scene.placeImageXY(new RectangleImage(10, 10, "solid", new Color(87, 206, 250)),
          this.solution.get(i).x * 10 + 5, this.solution.get(i).y * 10 + 5);
    }
    scene.placeImageXY(new TextImage(msg, 10, Color.black), 100, this.height * 10);
    return scene;
  }

  // returns a list of vertices at every point on this maze up this maze's
  // dimensions
  ArrayList<Vertex> makeVertices() {
    ArrayList<Vertex> vertices = new ArrayList<>();
    for (int row = 0; row < this.height; row += 1) {
      for (int column = 0; column < this.width; column += 1) {
        vertices.add(new Vertex(column, row));
      }
    }
    return vertices;
  }

  // returns a hash map that maps every vertex in this maze to itself
  HashMap<Vertex, Vertex> makeRepresentatives() {
    HashMap<Vertex, Vertex> representatives = new HashMap<>();
    for (Vertex v : this.vertices) {
      representatives.put(v, v);
    }
    return representatives;
  }

  // returns a list of connected random weighted edges at every vertex of this
  // maze
  ArrayList<Edge> makeUnsortedEdges() {
    ArrayList<Edge> edges = new ArrayList<>();
    // for every vertex of this maze, creates an edge
    for (int index = 0; index < this.vertices.size(); index += 1) {
      Vertex curr = this.vertices.get(index);
      // not left edge
      if (curr.x > 0) {
        curr.left = this.vertices.get(index - 1).right;
      }
      // not top edge
      if (curr.y > 0) {
        curr.up = this.vertices.get(index - this.width).down;
      }
      // not right edge
      if (curr.x < this.width - 1) {
        curr.right = new Edge(curr, this.vertices.get(index + 1), this.rand.nextInt(50));
        edges.add(curr.right);
      }
      // not bottom edge
      if (curr.y < this.height - 1) {
        curr.down = new Edge(curr, this.vertices.get(index + this.width), this.rand.nextInt(50));
        edges.add(curr.down);
      }
      curr.updateVertex();
    }
    return edges;
  }

  // returns a list of edges that is sorted by their weights in increasing order
  ArrayList<Edge> sortEdges(ArrayList<Edge> edges) {
    edges.sort(new EdgeWeight());
    return edges;
  }

  // creates a list of connected edges for this maze in the order of the weights
  ArrayList<Edge> unionFindData() {
    ArrayList<Edge> tempTree = new ArrayList<>();
    while (this.worklist.size() != 0) {
      Edge edge = this.worklist.get(0);
      if (this.find(edge.from).equals(this.find(edge.to))) {
        this.worklist.remove(0);
      }
      else {
        tempTree.add(edge);
        this.worklist.remove(0);
        this.union(edge.to, edge.from);
      }
    }
    return tempTree;
  }

  // looks for the representative of the given vertex
  Vertex find(Vertex vertex) {
    Vertex rep = this.representatives.get(vertex);
    while (!this.representatives.get(rep).equals(rep)) {
      rep = this.representatives.get(rep);
    }
    return rep;
  }

  // sets the given vertex value with the representative of the given key vertex
  void union(Vertex v1, Vertex v2) {
    this.representatives.put(this.find(v1), this.find(v2));
  }

  ArrayList<Vertex> makeSolution() {
    ArrayList<Vertex> tempSolution = new ArrayList<Vertex>();
    Deque<Vertex> alreadySeen = new ArrayDeque<Vertex>();
    Stack<Vertex> worklist = new Stack<Vertex>();
    // Initialize the worklist with the from vertex
    worklist.add(this.vertices.get(0));
    // As long as the worklist isn't empty...
    while (!worklist.isEmpty()) {
      Vertex next = worklist.remove();
      if (next.equals(this.vertices.get(this.vertices.size() - 1))) {
        Vertex index = this.vertices.get(this.vertices.size() - 1);
        tempSolution.add(index);
        while (!tempSolution.contains(this.vertices.get(0))) {
          index = this.cameFrom.get(index);
          tempSolution.add(index);
        }
        return tempSolution; // Success!
      }
      else if (alreadySeen.contains(next)) {
        // do nothing: we've already seen this one
      }
      else {
        // add all the neighbors of next to the worklist for further processing
        for (Edge e : next.outEdges) {
          if (this.edgesInTree.contains(e)) {
            if (e.to == next) {
              worklist.add(e.from);
              this.cameFrom.putIfAbsent(e.from, next);
            }
            else {
              worklist.add(e.to);
              this.cameFrom.putIfAbsent(e.to, next);
            }
          }
        }
        // add next to alreadySeen, since we're done with it
        alreadySeen.add(next);
      }
    }
    // We haven't found the to vertex, and there are no more to try
    return tempSolution;
  }

  // updates this world given a key, resetting the maze or moving the player
  public void onKeyEvent(String key) {
    if (key.equals("r")) {
      this.vertices = this.makeVertices();
      this.representatives = this.makeRepresentatives();
      this.worklist = this.sortEdges(this.makeUnsortedEdges());
      this.edgesInTree = this.unionFindData();
      this.player = new Player(this.vertices.get(0), new ArrayList<>());
      this.searchPath = new ArrayList<>();
      this.searchWorkList = new Queue<>();
      this.alreadySeen = new ArrayDeque<Vertex>();
      this.cameFrom = new HashMap<Vertex, Vertex>();
      this.solution = this.makeSolution();
      this.togglePath = true;
      this.wrongMoves = 0;
      this.algoWrongMoves = 0;
    }
    else if (key.equals("b")) {
      this.searchPath = new ArrayList<>();
      this.alreadySeen = new ArrayDeque<Vertex>();
      this.searchWorkList = new Queue<Vertex>(this.vertices.get(0));
      this.algoWrongMoves = 0;
      this.search(this.vertices.get(0), this.vertices.get(this.vertices.size() - 1),
          this.searchWorkList);
    }
    else if (key.equals("d")) {
      this.searchPath = new ArrayList<>();
      this.alreadySeen = new ArrayDeque<Vertex>();
      this.searchWorkList = new Stack<Vertex>(this.vertices.get(0));
      this.algoWrongMoves = 0;
      this.search(this.vertices.get(0), this.vertices.get(this.vertices.size() - 1),
          this.searchWorkList);
    }
    else if (key.equals("p")) {
      this.togglePath = !this.togglePath;
    }
    else if (this.validMove(key)) {
      if (key.equals("right")) {
        this.player.moveTo(this.vertices.get(this.vertices.indexOf(this.player.curr) + 1));
      }
      else if (key.equals("left")) {
        this.player.moveTo(this.vertices.get(this.vertices.indexOf(this.player.curr) - 1));
      }
      else if (key.equals("up")) {
        this.player.moveTo(this.vertices.get(this.vertices.indexOf(this.player.curr) - this.width));
      }
      else if (key.equals("down")) {
        this.player.moveTo(this.vertices.get(this.vertices.indexOf(this.player.curr) + this.width));
      }
    }
    if (!this.solution.contains(this.player.curr) && !this.player.path.contains(this.player.curr)) {
      this.wrongMoves += 1;
    }
  }

  // checks whether the given move is valid in this maze
  boolean validMove(String key) {
    if (key.equals("right") && this.player.curr.x < this.width - 1) {
      return this.edgesInTree.contains(this.player.curr.right);
    }
    else if (key.equals("left") && this.player.curr.x > 0) {
      return this.edgesInTree.contains(this.player.curr.left);
    }
    else if (key.equals("up") && this.player.curr.y > 0) {
      return this.edgesInTree.contains(this.player.curr.up);
    }
    else if (key.equals("down") && this.player.curr.y < this.height - 1) {
      return this.edgesInTree.contains(this.player.curr.down);
    }
    return false;
  }

  public void onTick() {
    if (!this.searchWorkList.isEmpty()) {
      this.search(this.vertices.get(0), this.vertices.get(this.vertices.size() - 1),
          this.searchWorkList);
    }
    if (this.alreadySeen.contains(this.vertices.get(this.vertices.size() - 1))
        || this.player.curr.equals(this.vertices.get(this.vertices.size() - 1))) {
      this.endOfWorld("The Maze Is Solved!");
    }
  }

  void search(Vertex from, Vertex to, ICollection<Vertex> worklist) {
    // Initialize the worklist with the from vertex
    Vertex next = worklist.remove();
    if (!this.solution.contains(next) && !this.searchPath.contains(next)) {
      this.algoWrongMoves += 1;
    }
    if (next.equals(to)) {
      this.endOfWorld("The Maze Is Solved!");
    }
    else if (this.alreadySeen.contains(next)) {
      // do nothing: we've already seen this one
    }
    else {
      // add all the neighbors of next to the worklist for further processing
      this.searchPath.add(next);
      for (Edge e : next.outEdges) {
        if (this.edgesInTree.contains(e)) {
          if (e.to == next) {
            worklist.add(e.from);
          }
          else {
            worklist.add(e.to);
          }
        }
      }
      // add next to alreadySeen, since we're done with it
      this.alreadySeen.add(next);
    }
    // We haven't found the to vertex, and there are no more to try
  }
}

// Represents a mutable collection of items
interface ICollection<T> {
  // Is this collection empty?
  boolean isEmpty();

  // EFFECT: adds the item to the collection
  void add(T item);

  // Returns the first item of the collection
  // EFFECT: removes that first item
  T remove();
}

class Stack<T> implements ICollection<T> {
  Deque<T> contents;

  Stack() {
    this.contents = new ArrayDeque<T>();
  }

  Stack(T item) {
    this.contents = new ArrayDeque<T>();
    this.add(item);
  }

  public boolean isEmpty() {
    return this.contents.isEmpty();
  }

  public void add(T item) {
    this.contents.addFirst(item);
  }

  public T remove() {
    return this.contents.removeFirst();
  }

}

class Queue<T> implements ICollection<T> {
  Deque<T> contents;

  Queue() {
    this.contents = new ArrayDeque<T>();
  }

  Queue(T item) {
    this.contents = new ArrayDeque<T>();
    this.add(item);
  }

  public boolean isEmpty() {
    return this.contents.isEmpty();
  }

  public void add(T item) {
    this.contents.addLast(item); // NOTE: Different from Stack!
  }

  public T remove() {
    return this.contents.removeFirst();
  }

}

// compares edges by their weights
class EdgeWeight implements Comparator<Edge> {

  // compares the given edges by their weights
  public int compare(Edge e1, Edge e2) {
    return e1.weight - e2.weight;
  }
}

// represents a player of the maze world
class Player {
  Vertex curr;
  ArrayList<Vertex> path;

  // constructor
  Player(Vertex curr, ArrayList<Vertex> path) {
    this.curr = curr;
    this.path = path;
  }

  // changes this player's current vertex to the given and adds the previous to
  // this path
  void moveTo(Vertex to) {
    this.path.add(this.curr);
    this.curr = to;
  }
}

// examples and tests for maze, edge, and vertex
class ExamplesMaze {

  Vertex zeroZero;
  Vertex oneZero;
  Vertex zeroOne;
  Vertex oneOne;

  Edge zeroZeroToOneZero;
  Edge zeroZeroToZeroOne;
  Edge oneZeroToOneOne;
  Edge zeroOneToOneOne;

  Maze oneByTwo;
  Maze twoByOne;
  Maze twoByThree;
  Maze threeByTwo;
  Maze oneByTwoTest;

  Maze oneByOneTest;
  Maze twoByTwoTest;
  Maze threeByThreeTest;

  Maze twoByTwo;
  Maze threeByThree;
  Maze fiveByFive;
  Maze tenByTen;
  Maze twentyByTwenty = new Maze(20, 20, new Random(1));
  Maze hundredBySixty = new Maze(100, 60);
  Maze twoHundredByTwoHundred = new Maze(200, 200);;

  // initializes data and examples for test cases
  void initData() {

    this.zeroZeroToOneZero = new Edge(this.zeroZero, this.oneZero, 0);
    this.zeroZeroToZeroOne = new Edge(this.zeroZero, this.zeroOne, 0);
    this.oneZeroToOneOne = new Edge(this.oneZero, this.oneOne, 10);
    this.zeroOneToOneOne = new Edge(this.zeroOne, this.oneOne, 0);

    this.zeroZero = new Vertex(null, null, this.zeroZeroToOneZero, this.zeroZeroToZeroOne, 0, 0);
    this.oneZero = new Vertex(null, this.zeroZeroToOneZero, null, this.oneZeroToOneOne, 1, 0);
    this.zeroOne = new Vertex(this.zeroZeroToZeroOne, null, this.zeroOneToOneOne, null, 0, 1);
    this.oneOne = new Vertex(this.oneZeroToOneOne, this.zeroOneToOneOne, null, null, 1, 1);

    fiveByFive = new Maze(5, 5);
    tenByTen = new Maze(10, 10);
    oneByTwo = new Maze(1, 2);
    twoByOne = new Maze(2, 1);
    twoByTwo = new Maze(2, 2);
    twoByThree = new Maze(2, 3);
    threeByTwo = new Maze(3, 2);

    oneByOneTest = new Maze(1, 1, new Random(1));
    oneByTwoTest = new Maze(1, 2, new Random(1));
    twoByTwoTest = new Maze(2, 2, new Random(2));
    threeByThreeTest = new Maze(3, 3, new Random(3));
  }

  // big bang for maze
  void testBigBang(Tester t) {
    this.initData();
    int worldLength = this.hundredBySixty.width * 10;
    int worldHeight = this.hundredBySixty.height * 10 + 50;
    if (worldLength < 200) {
      worldLength = 200;
    }
    if (worldHeight < 200) {
      worldHeight = 200;
    }
    double tickRate = .001;
    this.hundredBySixty.bigBang(worldLength, worldHeight, tickRate);
  }

  // tests the method updateVertex for class Vertex
  void testUpdateVertex(Tester t) {
    this.initData();
    Vertex oneTwo = new Vertex(1, 2);
    t.checkExpect(oneTwo.outEdges.isEmpty(), true);
    Edge oneOneToOneTwo = new Edge(this.oneOne, oneTwo, 0);
    oneTwo.left = oneOneToOneTwo;
    t.checkExpect(oneTwo.outEdges.isEmpty(), true);
    oneTwo.updateVertex();
    t.checkExpect(oneTwo.outEdges.isEmpty(), false);
    t.checkExpect(oneTwo.outEdges.contains(oneOneToOneTwo), true);
    t.checkExpect(oneTwo.outEdges.size(), 1);
  }

  // tests the method drawVerticalEdge for class Edge
  void testDrawVerticalEdge(Tester t) {
    this.initData();
    t.checkExpect(this.zeroZeroToZeroOne.drawVerticalEdge(),
        new RectangleImage(1, 10, OutlineMode.SOLID, Color.BLACK));
    t.checkExpect(this.oneZeroToOneOne.drawVerticalEdge(),
        new RectangleImage(1, 10, OutlineMode.SOLID, Color.BLACK));
  }

  // tests the method drawHorizontalEdge for class Edge
  void testDrawHorizontalEdge(Tester t) {
    this.initData();
    t.checkExpect(this.zeroZeroToOneZero.drawHorizontalEdge(),
        new RectangleImage(10, 1, OutlineMode.SOLID, Color.BLACK));
    t.checkExpect(this.zeroOneToOneOne.drawHorizontalEdge(),
        new RectangleImage(10, 1, OutlineMode.SOLID, Color.BLACK));
  }

  // tests the method makeScene for class Maze
  void testMakeScene(Tester t) {

  }

  // tests the method makeVertices for class Maze
  void testMakeVertices(Tester t) {

  }

  // tests the method makeRepresentatives for class Maze
  void testMakeRepresentatives(Tester t) {
    this.initData();
    HashMap<Vertex, Vertex> twoByTwoMap = new HashMap<>();
    twoByTwoMap = this.twoByTwo.makeRepresentatives();
    int hashMapSize = 0;
    for (int i = 0; i < this.twoByTwo.vertices.size(); i++) {
      t.checkExpect(this.twoByTwo.vertices.get(i), twoByTwoMap.get(this.twoByTwo.vertices.get(i)));
      hashMapSize += 1;
    }
    t.checkExpect(hashMapSize, 4);

    HashMap<Vertex, Vertex> fiveByFiveMap = new HashMap<>();
    fiveByFiveMap = this.fiveByFive.makeRepresentatives();
    hashMapSize = 0;
    for (int i = 0; i < this.fiveByFive.vertices.size(); i++) {
      t.checkExpect(this.fiveByFive.vertices.get(i),
          fiveByFiveMap.get(this.fiveByFive.vertices.get(i)));
      hashMapSize += 1;
    }
    t.checkExpect(hashMapSize, 25);
  }

  // tests the method makeUnsortedEdges for class Maze
  void testMakeUnsortedEdges(Tester t) {
  }

  // tests the method sortEdgesMethod for class Maze
  void testSortEdges(Tester t) {
    this.initData();
    t.checkExpect(this.oneByOneTest.sortEdges(new ArrayList<>()), new ArrayList<>());

    ArrayList<Edge> edges = new ArrayList<>(
        Arrays.asList(zeroZeroToOneZero, zeroZeroToZeroOne, oneZeroToOneOne, zeroOneToOneOne));
    t.checkExpect(this.oneByTwo.sortEdges(edges), new ArrayList<>(
        Arrays.asList(zeroZeroToOneZero, zeroZeroToZeroOne, zeroOneToOneOne, oneZeroToOneOne)));

    ArrayList<Vertex> vertices = new ArrayList<>();
    for (int i = 0; i < 3; i += 1) {
      for (int j = 0; j < 3; j += 1) {
        vertices.add(new Vertex(j, i));
      }
    }
    Edge e1 = new Edge(vertices.get(0), vertices.get(1), 10);
    Edge e2 = new Edge(vertices.get(0), vertices.get(2), 13);
    Edge e3 = new Edge(vertices.get(1), vertices.get(3), 32);
    Edge e4 = new Edge(vertices.get(2), vertices.get(3), 25);
    Edge e5 = new Edge(vertices.get(2), vertices.get(4), 7);
    Edge e6 = new Edge(vertices.get(4), vertices.get(4), 13);
    t.checkExpect(this.twoByTwo.sortEdges(new ArrayList<>(Arrays.asList(e1, e2, e3, e4, e5, e6))),
        new ArrayList<>(Arrays.asList(e5, e1, e2, e6, e4, e3)));
  }

  // tests the unionFindData method for class Maze
  void testUnionFindData(Tester t) {
    this.initData();
    Maze fourByFour = new Maze(4, 4); // initializing Mazes calls unionFindData
    Maze threeBySix = new Maze(6, 3);
    t.checkExpect(fourByFour.worklist.size(), 0);
    t.checkExpect(fourByFour.edgesInTree.size(), fourByFour.vertices.size() - 1);
    t.checkExpect(threeBySix.worklist.size(), 0);
    t.checkExpect(threeBySix.edgesInTree.size(), threeBySix.vertices.size() - 1);
  }

  // tests the find method for class Maze
  void testFind(Tester t) {
    this.initData();
    this.twoByTwo.representatives = new HashMap<Vertex, Vertex>();
    this.twoByTwo.representatives.put(this.zeroZero, this.zeroZero);
    this.twoByTwo.representatives.put(this.oneZero, this.oneOne);
    this.twoByTwo.representatives.put(this.oneOne, this.zeroOne);
    this.twoByTwo.representatives.put(this.zeroOne, this.zeroOne);
    t.checkExpect(this.twoByTwo.find(this.zeroZero), this.zeroZero);
    t.checkExpect(this.twoByTwo.find(this.oneZero), this.zeroOne);
    t.checkExpect(this.twoByTwo.find(this.oneOne), this.zeroOne);
    t.checkExpect(this.twoByTwo.find(this.zeroOne), this.zeroOne);
  }

  // tests the union method for class Maze
  void testUnion(Tester t) {
    this.initData();
    this.twoByTwo.representatives = new HashMap<Vertex, Vertex>();
    this.twoByTwo.representatives.put(this.zeroZero, this.zeroZero);
    this.twoByTwo.representatives.put(this.oneZero, this.oneZero);
    this.twoByTwo.representatives.put(this.oneOne, this.oneOne);
    this.twoByTwo.representatives.put(this.zeroOne, this.zeroOne);
    this.twoByTwo.union(this.oneZero, this.zeroZero);
    this.twoByTwo.union(this.oneOne, this.oneZero);
    this.twoByTwo.union(this.zeroOne, this.oneOne);
    t.checkExpect(this.twoByTwo.representatives.get(this.zeroZero), this.zeroZero);
    t.checkExpect(this.twoByTwo.representatives.get(this.oneZero), this.zeroZero);
    t.checkExpect(this.twoByTwo.representatives.get(this.oneOne), this.zeroZero);
    t.checkExpect(this.twoByTwo.representatives.get(this.zeroOne), this.zeroZero);
  }

  // tests the method makeSolution for class Maze
  void testMakeSolution(Tester t) {
    this.initData();
    this.twoByTwoTest.makeSolution();
    t.checkExpect(this.twoByTwoTest.solution.contains(this.twoByTwoTest.vertices.get(0)), true);
    t.checkExpect(this.twoByTwoTest.solution
        .contains(this.twoByTwoTest.vertices.get(this.twoByTwoTest.vertices.size() - 1)), true);
    t.checkExpect(this.twoByTwoTest.solution.size(), 3);
  }

  // tests the method onKeyEvent for class Maze
  void testOnKeyEvent(Tester t) {
    this.initData();
    this.twoByTwoTest.onKeyEvent("p");
    t.checkExpect(this.twoByTwoTest.togglePath, false);
    this.twoByTwoTest.onKeyEvent("right");
    t.checkExpect(this.twoByTwoTest.player.curr, this.twoByTwoTest.vertices.get(1));
    t.checkExpect(this.twoByTwoTest.player.path.contains(this.twoByTwoTest.vertices.get(0)), true);
    this.twoByTwoTest.onKeyEvent("left");
    t.checkExpect(this.twoByTwoTest.player.curr, this.twoByTwoTest.vertices.get(0));
    t.checkExpect(this.twoByTwoTest.player.path.contains(this.twoByTwoTest.vertices.get(1)), true);
    this.threeByThreeTest.onKeyEvent("down");
    t.checkExpect(this.threeByThreeTest.player.curr, this.threeByThreeTest.vertices.get(3));
    t.checkExpect(this.threeByThreeTest.player.path.contains(this.threeByThreeTest.vertices.get(0)),
        true);
    this.threeByThreeTest.onKeyEvent("up");
    t.checkExpect(this.threeByThreeTest.player.curr, this.threeByThreeTest.vertices.get(0));
    t.checkExpect(this.threeByThreeTest.player.path.contains(this.threeByThreeTest.vertices.get(3)),
        true);
    this.threeByThreeTest.onKeyEvent("down");
    this.threeByThreeTest.onKeyEvent("right");
    this.threeByThreeTest.onKeyEvent("down");
    t.checkExpect(this.threeByThreeTest.wrongMoves, 1);
    this.threeByThreeTest.onKeyEvent("b");
    t.checkExpect(this.threeByThreeTest.searchPath.size(), 1);
    t.checkExpect(this.threeByThreeTest.searchPath.contains(this.threeByThreeTest.vertices.get(0)),
        true);
    t.checkExpect(this.threeByThreeTest.searchWorkList.getClass(), new Queue<Vertex>().getClass());
    t.checkExpect(this.threeByThreeTest.alreadySeen.size(), 1);
    t.checkExpect(this.threeByThreeTest.alreadySeen.contains(this.threeByThreeTest.vertices.get(0)),
        true);
    t.checkExpect(this.threeByThreeTest.cameFrom.containsKey(this.threeByThreeTest.vertices.get(3)),
        true);
    t.checkExpect(
        this.threeByThreeTest.cameFrom.containsValue(this.threeByThreeTest.vertices.get(0)), true);
    t.checkExpect(this.threeByThreeTest.algoWrongMoves, 0);
    this.threeByThreeTest.onKeyEvent("d");
    t.checkExpect(this.threeByThreeTest.searchPath.size(), 1);
    t.checkExpect(this.threeByThreeTest.searchPath.contains(this.threeByThreeTest.vertices.get(0)),
        true);
    t.checkExpect(this.threeByThreeTest.searchWorkList.getClass(), new Stack<Vertex>().getClass());
    t.checkExpect(this.threeByThreeTest.alreadySeen.size(), 1);
    t.checkExpect(this.threeByThreeTest.alreadySeen.contains(this.threeByThreeTest.vertices.get(0)),
        true);
    t.checkExpect(this.threeByThreeTest.cameFrom.containsKey(this.threeByThreeTest.vertices.get(3)),
        true);
    t.checkExpect(
        this.threeByThreeTest.cameFrom.containsValue(this.threeByThreeTest.vertices.get(0)), true);
    t.checkExpect(this.threeByThreeTest.algoWrongMoves, 0);
    ArrayList<Edge> previousEdges = this.threeByThreeTest.edgesInTree;
    ArrayList<Vertex> previousSolution = this.threeByThreeTest.solution;
    HashMap<Vertex, Vertex> previousCameFrom = this.threeByThreeTest.cameFrom;
    this.threeByThreeTest.onKeyEvent("r");
    t.checkExpect(this.threeByThreeTest.edgesInTree.equals(previousEdges), false);
    t.checkExpect(this.threeByThreeTest.player.curr, this.threeByThreeTest.vertices.get(0));
    t.checkExpect(this.threeByThreeTest.player.path.isEmpty(), true);
    t.checkExpect(this.threeByThreeTest.searchPath.isEmpty(), true);
    t.checkExpect(this.threeByThreeTest.searchWorkList.isEmpty(), true);
    t.checkExpect(this.threeByThreeTest.alreadySeen.isEmpty(), true);
    t.checkExpect(this.threeByThreeTest.cameFrom.equals(previousCameFrom), false);
    t.checkExpect(this.threeByThreeTest.solution.equals(previousSolution), false);
    t.checkExpect(this.threeByThreeTest.togglePath, true);
    t.checkExpect(this.threeByThreeTest.algoWrongMoves, 0);
    t.checkExpect(this.threeByThreeTest.wrongMoves, 0);
  }

  // tests the validMove method for class Maze
  void testValidMove(Tester t) {
    this.initData();
    t.checkExpect(this.twoByTwoTest.validMove("left"), false);
    t.checkExpect(this.twoByTwoTest.validMove("right"), true);
    t.checkExpect(this.twoByTwoTest.validMove("up"), false);
    t.checkExpect(this.twoByTwoTest.validMove("down"), true);
    t.checkExpect(this.threeByThreeTest.validMove("left"), false);
    t.checkExpect(this.threeByThreeTest.validMove("right"), false);
    t.checkExpect(this.threeByThreeTest.validMove("up"), false);
    t.checkExpect(this.threeByThreeTest.validMove("down"), true);
  }

  // tests the onTick method for class Maze
  void testOnTick(Tester t) {
    this.initData();
    t.checkExpect(this.threeByThreeTest.searchWorkList.isEmpty(), true);
    this.threeByThreeTest.onTick();
    t.checkExpect(this.threeByThreeTest.searchWorkList.isEmpty(), true);
    this.threeByThreeTest.onKeyEvent("d");
    this.threeByThreeTest.onTick();
    t.checkExpect(this.threeByThreeTest.searchPath.contains(this.threeByThreeTest.vertices.get(3)),
        true);
    t.checkExpect(this.threeByThreeTest.searchPath.size(), 2);
    t.checkExpect(this.threeByThreeTest.searchWorkList.getClass(), new Stack<Vertex>().getClass());
    t.checkExpect(this.threeByThreeTest.alreadySeen.size(), 2);
    t.checkExpect(this.threeByThreeTest.alreadySeen.contains(this.threeByThreeTest.vertices.get(3)),
        true);
    t.checkExpect(this.threeByThreeTest.cameFrom.containsKey(this.threeByThreeTest.vertices.get(3)),
        true);
    t.checkExpect(
        this.threeByThreeTest.cameFrom.containsValue(this.threeByThreeTest.vertices.get(0)), true);
    t.checkExpect(this.threeByThreeTest.algoWrongMoves, 0);
    this.threeByThreeTest.onTick();
    t.checkExpect(this.threeByThreeTest.searchPath.contains(this.threeByThreeTest.vertices.get(4)),
        true);
    t.checkExpect(this.threeByThreeTest.searchPath.size(), 3);
    t.checkExpect(this.threeByThreeTest.alreadySeen.size(), 3);
    t.checkExpect(this.threeByThreeTest.alreadySeen.contains(this.threeByThreeTest.vertices.get(4)),
        true);
    t.checkExpect(this.threeByThreeTest.cameFrom.containsKey(this.threeByThreeTest.vertices.get(4)),
        true);
    t.checkExpect(
        this.threeByThreeTest.cameFrom.containsValue(this.threeByThreeTest.vertices.get(3)), true);
    t.checkExpect(this.threeByThreeTest.algoWrongMoves, 0);
    this.threeByThreeTest.onTick();
    t.checkExpect(this.threeByThreeTest.searchPath.contains(this.threeByThreeTest.vertices.get(7)),
        true);
    t.checkExpect(this.threeByThreeTest.searchPath.size(), 4);
    t.checkExpect(this.threeByThreeTest.alreadySeen.size(), 4);
    t.checkExpect(this.threeByThreeTest.alreadySeen.contains(this.threeByThreeTest.vertices.get(7)),
        true);
    t.checkExpect(this.threeByThreeTest.cameFrom.containsKey(this.threeByThreeTest.vertices.get(7)),
        true);
    t.checkExpect(
        this.threeByThreeTest.cameFrom.containsValue(this.threeByThreeTest.vertices.get(4)), true);
    t.checkExpect(this.threeByThreeTest.algoWrongMoves, 1);
  }

  // tests the search method for class Maze
  void testSearch(Tester t) {
    this.initData();
    this.twoByTwoTest.searchWorkList = new Stack<Vertex>(this.twoByTwoTest.vertices.get(0));
    this.twoByTwoTest.search(this.twoByTwoTest.vertices.get(0),
        this.twoByTwoTest.vertices.get(this.twoByTwoTest.vertices.size() - 1),
        this.twoByTwoTest.searchWorkList);
    t.checkExpect(this.twoByTwoTest.searchPath.contains(this.twoByTwoTest.vertices.get(0)), true);
    t.checkExpect(this.twoByTwoTest.searchPath.size(), 1);
    t.checkExpect(this.twoByTwoTest.searchWorkList.getClass(), new Stack<Vertex>().getClass());
    t.checkExpect(this.twoByTwoTest.alreadySeen.size(), 1);
    t.checkExpect(this.twoByTwoTest.alreadySeen.contains(this.twoByTwoTest.vertices.get(0)), true);
    t.checkExpect(this.twoByTwoTest.cameFrom.containsKey(this.twoByTwoTest.vertices.get(0)), true);
    t.checkExpect(this.twoByTwoTest.cameFrom.containsValue(this.twoByTwoTest.vertices.get(0)),
        true);
    t.checkExpect(this.threeByThreeTest.algoWrongMoves, 0);
    this.twoByTwoTest.search(this.twoByTwoTest.vertices.get(0),
        this.twoByTwoTest.vertices.get(this.twoByTwoTest.vertices.size() - 1),
        this.twoByTwoTest.searchWorkList);
    t.checkExpect(this.twoByTwoTest.searchPath.contains(this.twoByTwoTest.vertices.get(1)), false);
    t.checkExpect(this.twoByTwoTest.searchPath.contains(this.twoByTwoTest.vertices.get(2)), true);
    t.checkExpect(this.twoByTwoTest.searchPath.size(), 2);
    t.checkExpect(this.twoByTwoTest.searchWorkList.getClass(), new Stack<Vertex>().getClass());
    t.checkExpect(this.twoByTwoTest.alreadySeen.size(), 2);
    t.checkExpect(this.twoByTwoTest.alreadySeen.contains(this.twoByTwoTest.vertices.get(2)), true);
    t.checkExpect(this.twoByTwoTest.cameFrom.containsKey(this.twoByTwoTest.vertices.get(2)), true);
    t.checkExpect(this.twoByTwoTest.cameFrom.containsValue(this.twoByTwoTest.vertices.get(0)),
        true);
    t.checkExpect(this.twoByTwoTest.algoWrongMoves, 0);
  }

  // tests the compare method for class EdgeWeight Comparator
  void testCompare(Tester t) {
    this.initData();
    t.checkExpect(new EdgeWeight().compare(this.zeroZeroToOneZero, this.zeroZeroToOneZero), 0);
    t.checkExpect(new EdgeWeight().compare(this.zeroZeroToOneZero, this.zeroZeroToZeroOne), 0);
    t.checkExpect(new EdgeWeight().compare(this.zeroZeroToOneZero, this.oneZeroToOneOne), -10);
    t.checkExpect(new EdgeWeight().compare(this.oneZeroToOneOne, this.zeroZeroToZeroOne), 10);
  }
  
  void testMoveTo(Tester t) {
    this.initData();
    Player p = new Player(this.oneZero, new ArrayList<Vertex>(Arrays.asList(this.zeroZero)));
    p.moveTo(this.oneOne);
    t.checkExpect(p.curr, this.oneOne);
    t.checkExpect(p.path.contains(this.oneZero), true);
    p.moveTo(this.zeroOne);
    t.checkExpect(p.curr, this.zeroOne);
    t.checkExpect(p.path.contains(this.oneOne), true);
    p.moveTo(this.oneOne);
    t.checkExpect(p.curr, this.oneOne);
    t.checkExpect(p.path.contains(this.zeroOne), true);
  }

}