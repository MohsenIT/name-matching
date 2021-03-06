package dao;

import com.koloboke.collect.map.hash.HashLongObjMaps;
import com.koloboke.collect.set.hash.HashObjSets;
import dao.edge.E;
import dao.edge.TokenE;
import dao.edge.TokenE.NamePart;
import dao.vertex.ClusterV;
import dao.vertex.ElementV;
import dao.vertex.RefV;
import dao.vertex.V;
import de.zedlitz.phonet4java.Coder;
import de.zedlitz.phonet4java.Phonet2;
import de.zedlitz.phonet4java.Soundex;
import de.zedlitz.phonet4java.SoundexRefined;
import evaluation.paired.FMeasure;
import helper.IO;
import logic.matching.ClusterProfile;
import logic.matching.MatchResult;

import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class G {

    //region Fields
    private Set<E> es;
    private Map<Long, V> vs;
    //endregion

    //region Getters & Setters

    /**
     * Gets all vertices of the Graph.
     *
     * @return Map of graph vertices. The key is numeric vertex id and value is V.
     */
    public Map<Long, V> getVs() {
        return vs;
    }

    /**
     * Gets all graph vertices with specified type.
     *
     * @param type : a specific type of vertices
     * @return Set of graph vertices
     */
    public Set<V> getVs(V.Type type) {
        return vs.values().stream().filter(v -> v.getType() == type).collect(Collectors.toSet());
    }

    /**
     * Gets a vertex with id.
     *
     * @param id : Long id of a specific vertex
     * @return a vertex if exist else null
     */
    public V getV(Long id) {
        return vs.getOrDefault(id, null);
    }

    public void setVs(Map<Long, V> vs) {
        this.vs = vs;
    }

    /**
     * Add a single vertex to the Graph vertices.
     *
     * @param v : a vertex
     */
    public void addV(V v) {
        this.vs.put(v.getId(), v);
    }


    /**
     * Gets all edges of the Graph.
     *
     * @return Set of graph edges
     */
    public Set<E> getEs() {
        return es;
    }

    public void setEs(Set<E> es) {
        this.es = es;
    }

    /**
     * Add an edge to the Graph.
     *
     * @param inV    from vertex of edge
     * @param outV   to vertex of edge
     * @param type   type of edge
     * @param weight weight of edge
     */
    public void addE(V inV, V outV, E.Type type, Float weight) {
        E e = new E(inV, outV, type, weight);
        this.es.add(e);
        inV.addOutE(e);
        outV.addInE(e);
    }

    /**
     * Add an edge to the Graph Edges.
     *
     * @param e from vertex of edge
     */
    public void addE(E e) {
        this.es.add(e);
    }

    //endregion

    /**
     * Generate graph using vertices and edges adjacency list file
     *
     * @param vertexFilePath csv file contains vertices fields
     * @param edgeFilePath   csv file that store edges fields
     */
    public void init(String vertexFilePath, String edgeFilePath) {
        List<String[]> vertices = IO.readCSVLines(vertexFilePath);
        vs = HashLongObjMaps.newMutableMap(40000);
        checkNotNull(vertices, "vertex list should not be null.");
        for (int i = 1; i < vertices.size(); i++) {
            String[] l = vertices.get(i);
            V v = V.Type.isReference(l[2]) ? new RefV(l[0], l[1], l[3]) :
                    V.Type.isElement(l[2]) ? new ElementV(l[0], l[1], l[2], l[3], 0) :
                            new V(l[0], l[1], l[2], l[3]);
            vs.put(Long.valueOf(l[0]), v);
        }

        List<String[]> edges = IO.readCSVLines(edgeFilePath);
        checkNotNull(edges, "edge list should not be null.");
        es = HashObjSets.newMutableSet(80000);
        for (int i = 1; i < edges.size(); i++) {
            String[] l = edges.get(i);

            V inV = vs.get(Long.valueOf(l[0]));
            V outV = vs.get(Long.valueOf(l[1]));
            E e = l[4].equals("REF_TKN") ?
                    new TokenE((RefV) inV, (ElementV) outV, l[4], l[5]) : new E(inV, outV, l[4], l[5]);
            inV.addOutE(e);
            outV.addInE(e);
            es.add(e);
        }
        System.out.println("\tGraph object is initiated successfully.");
    }


    /**
     * Assign a cluster vertex to each REFERENCE vertices.
     * The clusters change during resolution.
     */
    public void initClusters() {
        long maxId = vs.keySet().stream().mapToLong(Long::longValue).max().orElse(1);
        Set<RefV> refVs = vs.values().stream().filter(e -> e.getType() == V.Type.REFERENCE)
                .map(RefV.class::cast).collect(Collectors.toSet());
        for (RefV refV : refVs) {
            ClusterV clusV = new ClusterV(++maxId, refV);
            E clusE = new E(clusV, refV, E.Type.CLS_REF, 1.0f);
            refV.addInE(clusE);
            clusV.addOutE(clusE);
            this.addE(clusE);
            this.addV(clusV);
        }
        System.out.println("\tA Cluster vertex is assigned to each REF vertex");
    }

    /**
     * Assign name's part type (firstname, lastname , ...) to the REF_TKN edges
     */
    public void initNamesPart() {
        List<RefV> refVs = vs.values().stream().filter(e -> e.getType() == V.Type.REFERENCE)
                .map(RefV.class::cast).collect(Collectors.toList());
        for (RefV refV : refVs) {
            List<TokenE> tokenEs = refV.getOutE(E.Type.REF_TKN).stream().map(e -> (TokenE) e).sorted(
                    Comparator.comparing(TokenE::getIsAbbr)
                            .thenComparing(TokenE::getOrder, Comparator.reverseOrder()))
                    .collect(Collectors.toList());

            TokenE lname, fname;
            lname = tokenEs.get(0);
            lname.setNamePart(NamePart.LASTNAME);
            tokenEs.remove(lname);
            fname = tokenEs.stream().sorted(Comparator.comparing(TokenE::getOrder)).findFirst().orElse(null);
            if (fname != null) {
                fname.setNamePart(NamePart.FIRSTNAME);
                tokenEs.remove(fname);
                for (TokenE e : tokenEs) {
                    if (e.getOrder() > lname.getOrder())
                        e.setNamePart(NamePart.SUFFIX);
                    else if (e.getOrder() > fname.getOrder() && e.getOrder() < lname.getOrder())
                        e.setNamePart(NamePart.MIDDLENAME);
                    else e.setNamePart(NamePart.PREFIX);
                }
            }

        }
        System.out.println("\tInitial Name's part is assigned to REF_TKN edges.");
    }

    /**
     * update cluster edges according to the clustering result
     *
     * @param clusters a map of cluster Vs and their representative
     */
    public void updateClusters(Map<RefV, Collection<RefV>> clusters) {
        for (Map.Entry<RefV, Collection<RefV>> cluster : clusters.entrySet()) {
            for (RefV v : cluster.getValue())
                v.replaceReferenceCluster(cluster.getKey());
        }
    }

    /**
     * update cluster edges according to the their actual resolved_id to calculate max achievable F1.
     *
     * @param allCandidatesVs all vertices in the candidates collection
     */
    public void updateClustersToRealClusters(Collection<RefV> allCandidatesVs) {
        Queue<RefV> queue = new LinkedList<>(allCandidatesVs);
        while (!queue.isEmpty()) {
            RefV refV = queue.peek();
            List<RefV> vsInRID = refV.getInV(E.Type.RID_REF).iterator().next().getOutV(E.Type.RID_REF).stream()
                    .filter(queue::contains).map(RefV.class::cast).collect(Collectors.toList());
            for (RefV v : vsInRID)
                v.replaceReferenceCluster(refV);
            queue.removeAll(vsInRID);
        }
    }

    /**
     * update cluster edges according to the their actual resolved_id to calculate max achievable F1.
     *
     * @param g whole graph
     */
    @SuppressWarnings("Duplicates")
    public void updateToMaxAchievableRecall(G g) {
        List<RefV> refVs = g.getVs(V.Type.REFERENCE).stream().map(RefV.class::cast).collect(toList());
        Map<RefV, Boolean> refsToNotVisited = refVs.stream().collect(Collectors.toMap(Function.identity(), x -> true, (a, b) -> a, LinkedHashMap::new));
        for (RefV v : refsToNotVisited.keySet()) {
            if (!refsToNotVisited.get(v))
                continue;
            Queue<RefV> queue = new LinkedList<>(Collections.singletonList(v));
            refsToNotVisited.put(v, false);
            while (!queue.isEmpty()) {
                RefV u = queue.remove();
                V resIdV = u.getRefResolvedIdV();
                List<RefV> coResAdjs = u.getInOutV(E.Type.REF_REF).stream().map(e -> (RefV) e)
                        .filter(r -> refsToNotVisited.get(r) && r.getRefResolvedIdV().equals(resIdV)).collect(toList());
                for (RefV adj : coResAdjs) {
                    queue.add(adj);
                    refsToNotVisited.put(adj, false);
                    adj.replaceReferenceCluster(v);
                }
            }
        }
    }


    /**
     * update cluster edges according to the their actual resolved_id to calculate max achievable F1.
     *
     * @param g whole graph
     * @param goldPairsFilePath
     */
    @SuppressWarnings("Duplicates")
    public void updateToMaxAchievableRecallPairwise(G g, String goldPairsFilePath) {
        List<RefV> refVs = g.getVs(V.Type.REFERENCE).stream().map(RefV.class::cast).collect(toList());
        int a = 0, all = 0;
        List<String[]> goldPairs = IO.readCSVLines(goldPairsFilePath).stream().skip(1).collect(toList());
        for (String[] gold : goldPairs) {
            all++;
            RefV s = (RefV) g.getV(Long.valueOf(gold[0]));
            RefV t = (RefV) g.getV(Long.valueOf(gold[1]));
            Map<RefV, Boolean> refsToNotVisited = refVs.stream().collect(Collectors.toMap(Function.identity(), x -> true));

            Queue<RefV> queue = new LinkedList<>(Collections.singletonList(s));
            refsToNotVisited.put(s, false);
            while (!queue.isEmpty()) {
                RefV u = queue.remove();
                List<RefV> coResAdjs = u.getInOutV(E.Type.REF_REF).stream().map(RefV.class::cast).filter(refsToNotVisited::get).collect(toList());
                for (RefV adj : coResAdjs) {
                    if(adj.equals(t)){
                        t.replaceReferenceCluster(s);
                        a++;
                        while(!queue.isEmpty())queue.remove();
                        break;
                    }
                    queue.add(adj);
                    refsToNotVisited.put(adj, false);
                }
            }
        }
        System.out.println(a);
    }

    public void updateClustersToStringMatches() {
        Coder c = new Phonet2();
        Map<String, List<RefV>> phoneMap = getVs(V.Type.REFERENCE).stream().map(RefV.class::cast)
                .collect(Collectors.groupingBy(v -> c.code(v.getVal())));
        for (List<RefV> refVs : phoneMap.values()) {
            RefV refV = refVs.remove(0);
            for (RefV v : refVs) {
                v.replaceReferenceCluster(refV);
            }
        }
    }

    /**
     * update the clusterCnt field of all {@code ElementV}s
     *
     * @param maxUpdateLevel max level to update cluster count in the graph
     *                       note that level 0 is REF type, 1 is TKN type , and etc.
     */
    public void updateAncestorClusterCnt(Integer maxUpdateLevel) {
        checkNotNull(maxUpdateLevel);
        checkArgument(maxUpdateLevel >= 1 && maxUpdateLevel <= 3, "maxUpdateLevel must be between [1, 3].");
        List<ElementV> elementVs = vs.values().stream()
                .filter(v -> v.getLevel() > 0 && v.getLevel() <= maxUpdateLevel)
                .map(ElementV.class::cast)
                .sorted(Comparator.comparing(V::getLevel))
                .collect(Collectors.toList());
        for (ElementV v : elementVs) {
            int clusterCnt = v.getInE().entrySet().stream()
                    .filter(t -> t.getKey().isInterLevel())
                    .flatMapToInt(e -> e.getValue().stream()
                            .mapToInt(x -> x.getInV() instanceof RefV ? 1 : ((ElementV) x.getInV()).getClusterCount())
                    ).sum();
            v.setClusterCount(clusterCnt);
        }
        System.out.printf("\tClusterCount property of every vertex of level>0 is updated up to level %d.\r\n", maxUpdateLevel);
    }

    /**
     * update the clusterCnt field of all vertices with max of possible levels (SIMILAR).
     */
    public void updateAncestorClusterCnt() {
        updateAncestorClusterCnt(V.Type.maxLevel);
    }


}
