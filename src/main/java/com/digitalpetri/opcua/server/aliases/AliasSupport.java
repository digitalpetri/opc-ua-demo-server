package com.digitalpetri.opcua.server.aliases;

import com.digitalpetri.opcua.server.namespace.demo.DemoNamespace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.milo.opcua.sdk.server.AbstractLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.aliases.AliasCategoryConfig;
import org.eclipse.milo.opcua.sdk.server.aliases.AliasManager;
import org.eclipse.milo.opcua.sdk.server.aliases.AliasManagerConfig;
import org.eclipse.milo.opcua.sdk.server.aliases.AliasTarget;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Publishes and owns the demo server's OPC UA Part 17 alias categories and aliases. */
public final class AliasSupport extends AbstractLifecycle {

  static final String STATIC_CATEGORY_IDENTIFIER = "Aliases/MiloDemoStatic";
  static final String DYNAMIC_CATEGORY_IDENTIFIER = "Aliases/MiloDemoDynamic";
  static final String VERSION_STORE_RELATIVE_PATH = "aliases/versions.properties";

  private static final List<String> ALIASED_DATA_TYPES =
      List.of("Boolean", "Int32", "UInt32", "Double", "String", "DateTime");

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final DemoNamespace demoNamespace;
  private final AliasManager aliasManager;
  private final boolean dynamicNodesEnabled;
  private final List<CreatedAlias> createdAliases = new ArrayList<>();
  private final List<NodeId> createdCategoryIds = new ArrayList<>();

  private record CreatedAlias(NodeId categoryId, String aliasName) {}

  /**
   * Creates demo alias support for {@code server}.
   *
   * @param server the server whose standard alias hierarchy is managed.
   * @param demoNamespace the namespace that hosts the demo category and aliases.
   * @param dataDirPath the server data directory used for persistent version state.
   * @param findAliasVerboseEnabled whether to expose the optional verbose lookup Methods.
   * @param dynamicNodesEnabled whether the dynamic demo nodes and their alias category are enabled.
   */
  public AliasSupport(
      OpcUaServer server,
      DemoNamespace demoNamespace,
      Path dataDirPath,
      boolean findAliasVerboseEnabled,
      boolean dynamicNodesEnabled) {

    this.demoNamespace = demoNamespace;
    this.dynamicNodesEnabled = dynamicNodesEnabled;

    var managerConfig =
        AliasManagerConfig.builder()
            .versionStore(
                new FileAliasVersionStore(
                    dataDirPath.resolve(VERSION_STORE_RELATIVE_PATH), server.getNamespaceTable()))
            .nodeNamespaceIndex(demoNamespace.getNamespaceIndex())
            .findAliasVerboseEnabled(findAliasVerboseEnabled)
            .build();

    aliasManager = new AliasManager(server, managerConfig);
  }

  @Override
  protected void onStartup() {
    aliasManager.startup();

    try {
      NodeId staticCategoryId = addCategory(STATIC_CATEGORY_IDENTIFIER, "MiloDemoStatic");
      for (String dataType : ALIASED_DATA_TYPES) {
        addAlias(staticCategoryId, "Demo.Static." + dataType, "Demo.Variants.Scalar." + dataType);
      }

      if (dynamicNodesEnabled) {
        NodeId dynamicCategoryId = addCategory(DYNAMIC_CATEGORY_IDENTIFIER, "MiloDemoDynamic");
        for (String dataType : ALIASED_DATA_TYPES) {
          addAlias(dynamicCategoryId, "Demo.Dynamic." + dataType, "Demo.Dynamic." + dataType);
        }
      }
    } catch (Exception e) {
      removeDemoNodes();

      if (aliasManager.isRunning()) {
        aliasManager.shutdown();
      }

      throw new IllegalStateException("failed to initialize OPC UA Alias Names support", e);
    }
  }

  @Override
  protected void onShutdown() {
    removeDemoNodes();

    if (aliasManager.isRunning()) {
      aliasManager.shutdown();
    }
  }

  private NodeId addCategory(String categoryIdentifier, String browseName) throws UaException {
    NodeId categoryId = new NodeId(demoNamespace.getNamespaceIndex(), categoryIdentifier);

    var categoryConfig =
        new AliasCategoryConfig(
            categoryId,
            NodeIds.TagVariables,
            new QualifiedName(demoNamespace.getNamespaceIndex().intValue(), browseName),
            demoNamespace.getDemoNodeManager(),
            name -> new NodeId(demoNamespace.getNamespaceIndex(), "Aliases/" + name),
            true,
            false,
            false);

    aliasManager.addCategory(categoryConfig);
    createdCategoryIds.add(categoryId);

    return categoryId;
  }

  private void addAlias(NodeId categoryId, String aliasName, String targetIdentifier)
      throws UaException {

    var target =
        new AliasTarget(
            new NodeId(demoNamespace.getNamespaceIndex(), targetIdentifier).expanded(),
            null,
            NodeIds.AliasFor);

    aliasManager.addAlias(categoryId, aliasName, List.of(target));
    createdAliases.add(new CreatedAlias(categoryId, aliasName));
  }

  private void removeDemoNodes() {
    if (!aliasManager.isRunning()) {
      createdAliases.clear();
      createdCategoryIds.clear();
      return;
    }

    for (int i = createdAliases.size() - 1; i >= 0; i--) {
      CreatedAlias alias = createdAliases.get(i);
      try {
        aliasManager.deleteAlias(alias.categoryId(), alias.aliasName(), null);
      } catch (UaException e) {
        logger.warn("Failed to remove demo alias {}", alias.aliasName(), e);
      }
    }
    createdAliases.clear();

    for (int i = createdCategoryIds.size() - 1; i >= 0; i--) {
      NodeId categoryId = createdCategoryIds.get(i);
      try {
        aliasManager.removeCategory(categoryId);
      } catch (UaException e) {
        logger.warn("Failed to remove demo alias category {}", categoryId, e);
      }
    }
    createdCategoryIds.clear();
  }
}
