/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.nodes.extended;

import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.TypeReference;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.StampProvider;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.type.StampTool;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Loads an object's hub. The object is not null-checked by this operation.
 */
@NodeInfo
public final class LoadHubNode extends FloatingNode implements Lowerable, Canonicalizable, Virtualizable {

    public static final NodeClass<LoadHubNode> TYPE = NodeClass.create(LoadHubNode.class);
    @Input ValueNode value;

    public ValueNode getValue() {
        return value;
    }

    private static Stamp hubStamp(StampProvider stampProvider, ValueNode value) {
        assert value.stamp() instanceof ObjectStamp;
        return stampProvider.createHubStamp(((ObjectStamp) value.stamp()));
    }

    public static ValueNode create(ValueNode value, StampProvider stampProvider, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        Stamp stamp = hubStamp(stampProvider, value);
        ValueNode synonym = findSynonym(value, stamp, metaAccess, constantReflection);
        if (synonym != null) {
            return synonym;
        }
        return new LoadHubNode(stamp, value);
    }

    public LoadHubNode(@InjectedNodeParameter StampProvider stampProvider, ValueNode value) {
        this(hubStamp(stampProvider, value), value);
    }

    public LoadHubNode(Stamp stamp, ValueNode value) {
        super(TYPE, stamp);
        this.value = value;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        MetaAccessProvider metaAccess = tool.getMetaAccess();
        ValueNode curValue = getValue();
        ValueNode newNode = findSynonym(curValue, stamp(), metaAccess, tool.getConstantReflection());
        if (newNode != null) {
            return newNode;
        }
        return this;
    }

    public static ValueNode findSynonym(ValueNode curValue, Stamp stamp, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        TypeReference type = StampTool.typeReferenceOrNull(curValue);
        if (type != null && type.isExact()) {
            return ConstantNode.forConstant(stamp, constantReflection.asObjectHub(type.getType()), metaAccess);
        }
        return null;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getValue());
        TypeReference type = StampTool.typeReferenceOrNull(alias);
        if (type != null && type.isExact()) {
            tool.replaceWithValue(ConstantNode.forConstant(stamp(), tool.getConstantReflectionProvider().asObjectHub(type.getType()), tool.getMetaAccessProvider(), graph()));
        }
    }
}
