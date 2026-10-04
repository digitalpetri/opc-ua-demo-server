package com.digitalpetri.opcua.server.namespace.demo.ctt;

import static com.digitalpetri.opcua.server.namespace.demo.Util.deriveChildNodeId;

import com.digitalpetri.opcua.server.namespace.demo.DemoAddressSpaceFragment;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.Reference.Direction;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceComposite;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.stack.core.ReferenceTypes;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

public class PathsFragment extends DemoAddressSpaceFragment {

  private final SimpleAddressSpaceFilter filter;

  private final NodeId parentNodeId;
  private final UShort namespaceIndex;

  public PathsFragment(
      OpcUaServer server,
      AddressSpaceComposite composite,
      NodeId parentNodeId,
      UShort namespaceIndex) {

    super(server, composite);

    this.parentNodeId = parentNodeId;
    this.namespaceIndex = namespaceIndex;

    filter = SimpleAddressSpaceFilter.create(getNodeManager()::containsNode);

    getLifecycleManager().addStartupTask(this::addNodes);
  }

  @Override
  public AddressSpaceFilter getFilter() {
    return filter;
  }

  private void addNodes() {
    var pathsFolder =
        new UaFolderNode(
            getNodeContext(),
            deriveChildNodeId(parentNodeId, "Paths"),
            new QualifiedName(namespaceIndex, "Paths"),
            new LocalizedText("Paths"));

    getNodeManager().addNode(pathsFolder);

    pathsFolder.addReference(
        new Reference(
            pathsFolder.getNodeId(),
            ReferenceTypes.HasComponent,
            parentNodeId.expanded(),
            Direction.INVERSE));

    // Add 10 folders each recursively underneath the next one.
    NodeId currentParent = pathsFolder.getNodeId();

    for (int i = 0; i < 10; i++) {
      var folder =
          new UaFolderNode(
              getNodeContext(),
              deriveChildNodeId(currentParent, "Folder%d".formatted(i)),
              new QualifiedName(namespaceIndex, "Folder%d".formatted(i)),
              new LocalizedText("Folder%d".formatted(i)));

      getNodeManager().addNode(folder);

      folder.addReference(
          new Reference(
              folder.getNodeId(),
              ReferenceTypes.HasComponent,
              currentParent.expanded(),
              Direction.INVERSE));

      currentParent = folder.getNodeId();
    }
  }
}
