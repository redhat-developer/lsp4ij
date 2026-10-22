/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterServerSettings;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.ExceptionBreakpointsFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * "Exception Breakpoints" panel which shows the DAP exceptions breakpoints filter in a table
 * which can be enabled/disabled with checkbox, and given a condition when the filter supports one.
 */
public class DAPExceptionBreakpointsPanel extends BorderLayoutPanel implements Disposable {

    public static final @NotNull @NonNls String ID = "dap-breakpoints-panel";

    static final EnabledColumnInfo ENABLED_COLUMN = new EnabledColumnInfo();
    static final NameColumnInfo NAME_COLUMN = new NameColumnInfo();
    private static final int CONDITION_COLUMN_INDEX = 2;

    private final @NotNull DAPBreakpointHandlerBase<?> breakpointHandler;
    private final @NotNull ListTableModel<ExceptionBreakpointsFilter> myModel;
    private final @NotNull TableView<ExceptionBreakpointsFilter> myTable;

    public DAPExceptionBreakpointsPanel(@NotNull DAPBreakpointHandlerBase<?> breakpointHandler) {
        this.breakpointHandler = breakpointHandler;

        // Create Table view
        this.myModel = new ListTableModel<>(new ColumnInfo[]{ENABLED_COLUMN, NAME_COLUMN, new ConditionColumnInfo()});
        this.myModel.setSortable(true);
        this.myTable = new TableView<>(this.myModel);
        this.addToCenter(ScrollPaneFactory.createScrollPane(this.myTable));
        TableUtil.setupCheckboxColumn(this.myTable.getColumnModel().getColumn(0));

        // Load table view with DAP exceptions breakpoints filter
        // stored in settings
        var settings = getFilterSettings();
        var filtersSettings = settings.getExceptionBreakpointsFilters();
        if (filtersSettings != null) {
            refreshModel(filtersSettings);
        }
        myModel.addTableModelListener(e -> {
            if (e.getColumn() == 0 || e.getColumn() == CONDITION_COLUMN_INDEX) {
                // An "exception breakpoint filter" item has been selected/unselected, or its condition changed
                breakpointHandler.sendExceptionBreakpointFilters();
            }
        });
    }

    private static class EnabledColumnInfo extends ColumnInfo<ExceptionBreakpointsFilter, Boolean> {
        EnabledColumnInfo() {
            super("");
        }

        public Class<?> getColumnClass() {
            return Boolean.class;
        }

        public @Nullable Boolean valueOf(ExceptionBreakpointsFilter item) {
            return item.getDefault_() != null && item.getDefault_();
        }

        public boolean isCellEditable(ExceptionBreakpointsFilter item) {
            return true;
        }

        public void setValue(ExceptionBreakpointsFilter item, Boolean value) {
            item.setDefault_(value);
        }
    }

    private static class NameColumnInfo extends ColumnInfo<ExceptionBreakpointsFilter, ExceptionBreakpointsFilter> {

        NameColumnInfo() {
            super(CommonBundle.message("title.name", new Object[0]));
        }

        public @Nullable ExceptionBreakpointsFilter valueOf(ExceptionBreakpointsFilter filter) {
            return filter;
        }

        public Class<?> getColumnClass() {
            return ExceptionBreakpointsFilter.class;
        }

        public @Nullable TableCellRenderer getRenderer(ExceptionBreakpointsFilter producer) {
            return new ColoredTableCellRenderer() {
                protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
                    if (value instanceof ExceptionBreakpointsFilter filter) {
                        String label = StringUtils.isNotBlank(filter.getLabel()) ? filter.getLabel() : filter.getFilter();
                        this.append(label);
                        String tooltip = filter.getDescription();
                        if (StringUtils.isNotBlank(tooltip)) {
                            super.setToolTipText(tooltip);
                        }
                        super.setIcon(AllIcons.Debugger.Db_exception_breakpoint);
                        this.setTransparentIconBackground(true);
                    }
                }
            };
        }
    }

    private class ConditionColumnInfo extends ColumnInfo<ExceptionBreakpointsFilter, String> {

        ConditionColumnInfo() {
            super(DAPBundle.message("dap.panels.exception.breakpoints.condition"));
        }

        public @Nullable String valueOf(ExceptionBreakpointsFilter filter) {
            String condition = getCondition(filter.getFilter());
            return condition != null ? condition : "";
        }

        public boolean isCellEditable(ExceptionBreakpointsFilter filter) {
            return supportsCondition(filter);
        }

        public void setValue(ExceptionBreakpointsFilter filter, String condition) {
            setCondition(filter.getFilter(), condition);
        }

        public @Nullable TableCellRenderer getRenderer(ExceptionBreakpointsFilter filter) {
            return new ColoredTableCellRenderer() {
                protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
                    if (value instanceof String condition && !condition.isEmpty()) {
                        this.append(condition);
                    } else if (supportsCondition(filter) && StringUtils.isNotBlank(filter.getConditionDescription())) {
                        // What the condition means for this filter, until one is given
                        this.append(filter.getConditionDescription(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    }
                }
            };
        }
    }

    /**
     * Returns the condition of the given exception breakpoint filter, and null if it has none.
     *
     * @param filterId the exception breakpoint filter id.
     * @return the condition of the given exception breakpoint filter, and null if it has none.
     */
    public @Nullable String getCondition(@Nullable String filterId) {
        var conditions = getFilterSettings().getExceptionBreakpointsFilterConditions();
        return filterId != null && conditions != null ? conditions.get(filterId) : null;
    }

    /**
     * Sets the condition of the given exception breakpoint filter, which the DAP server uses when the filter
     * supports conditions. An empty condition removes it.
     *
     * @param filterId  the exception breakpoint filter id.
     * @param condition the condition, or null to remove it.
     */
    public void setCondition(@NotNull String filterId, @Nullable String condition) {
        var settings = getFilterSettings();
        var conditions = settings.getExceptionBreakpointsFilterConditions() != null ?
                new TreeMap<>(settings.getExceptionBreakpointsFilterConditions()) : new TreeMap<String, String>();
        if (StringUtils.isBlank(condition)) {
            conditions.remove(filterId);
        } else {
            conditions.put(filterId, condition.trim());
        }
        settings.setExceptionBreakpointsFilterConditions(conditions);
    }

    private static boolean supportsCondition(@NotNull ExceptionBreakpointsFilter filter) {
        return Boolean.TRUE.equals(filter.getSupportsCondition());
    }

    /**
     * Refresh the table view with the given DAP breakpoint exception filters.
     *
     * @param filters the DAP breakpoint exception filters.
     * @return the DAP breakpoint exception filters which are enabled.
     */
    public Collection<ExceptionBreakpointsFilter> refresh(@NotNull ExceptionBreakpointsFilter[] filters) {
        // Store filter as key and ExceptionBreakpointsFilter instance as value
        Map<String, ExceptionBreakpointsFilter> map = filters == null ? Collections.emptyMap() :
                Arrays.stream(filters)
                        .filter(f -> f.getFilter() != null)
                        .collect(Collectors.toMap(
                                ExceptionBreakpointsFilter::getFilter,
                                filter -> filter
                        ));

        var settings = getFilterSettings();
        // Update filters item.default with the settings.default
        var filtersSettings = settings.getExceptionBreakpointsFilters();
        if (filtersSettings != null) {
            for (var filterSettings : filtersSettings) {
                String filter = filterSettings.getFilter();
                var newFilter = map.get(filter);
                if (newFilter != null) {
                    newFilter.setDefault_(filterSettings.getDefault_());
                }
            }
        }
        // Update settings
        settings.setExceptionBreakpointsFilters(map.values());
        // Refresh the list of exception breakpoints
        refreshModel(map.values());
        // Returns list of exception breakpoints which are enabled
        return getApplicableFilters();
    }

    private void refreshModel(@NotNull Collection<ExceptionBreakpointsFilter> filters) {
        this.myModel.setItems(new ArrayList<>(filters));
    }

    private UserDefinedDebugAdapterServerSettings.@NotNull FilterItemSettings getFilterSettings() {
        var serverId = breakpointHandler.getDebugAdapterDescriptor().getId();
        var settings = UserDefinedDebugAdapterServerSettings.getInstance().getFilterSettings(serverId);
        if (settings == null) {
            settings = new UserDefinedDebugAdapterServerSettings.FilterItemSettings();
            UserDefinedDebugAdapterServerSettings.getInstance().setFilterSettings(serverId, settings);
        }
        return settings;
    }

    public JComponent getDefaultFocusedComponent() {
        return this.myTable;
    }

    public List<ExceptionBreakpointsFilter> getApplicableFilters() {
        return myModel.getItems()
                .stream()
                .filter(DAPExceptionBreakpointsPanel::isEnabled)
                .toList();
    }

    @Override
    public void dispose() {

    }

    private static boolean isEnabled(@NotNull ExceptionBreakpointsFilter filter) {
        return filter.getDefault_() != null && filter.getDefault_();
    }
}
