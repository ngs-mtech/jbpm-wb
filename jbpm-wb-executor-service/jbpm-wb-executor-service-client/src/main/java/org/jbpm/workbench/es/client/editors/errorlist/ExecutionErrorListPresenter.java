/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.workbench.es.client.editors.errorlist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.view.client.Range;
import org.dashbuilder.dataset.DataSet;
import org.jboss.errai.common.client.api.Caller;
import org.jbpm.workbench.common.client.dataset.AbstractDataSetReadyCallback;
import org.jbpm.workbench.common.client.list.AbstractMultiGridPresenter;
import org.jbpm.workbench.common.client.list.MultiGridView;
import org.jbpm.workbench.common.client.menu.RestoreDefaultFiltersMenuBuilder;
import org.jbpm.workbench.df.client.filter.FilterSettings;
import org.jbpm.workbench.df.client.filter.FilterSettingsBuilderHelper;
import org.jbpm.workbench.es.client.i18n.Constants;
import org.jbpm.workbench.es.client.perspectives.ErrorManagementPerspective;
import org.jbpm.workbench.es.model.ExecutionErrorSummary;
import org.jbpm.workbench.es.model.events.ExecErrorChangedEvent;
import org.jbpm.workbench.es.model.events.ExecErrorSelectionEvent;
import org.jbpm.workbench.es.model.events.ExecErrorWithDetailsRequestEvent;
import org.jbpm.workbench.es.service.ExecutorService;
import org.jbpm.workbench.es.util.ExecutionErrorType;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchScreen;
import org.uberfire.client.mvp.PlaceStatus;
import org.uberfire.client.workbench.widgets.common.ErrorPopupPresenter;
import org.uberfire.ext.widgets.common.client.menu.RefreshMenuBuilder;
import org.uberfire.mvp.impl.DefaultPlaceRequest;
import org.uberfire.workbench.model.menu.MenuFactory;
import org.uberfire.workbench.model.menu.Menus;

import static org.dashbuilder.dataset.filter.FilterFactory.between;
import static org.dashbuilder.dataset.filter.FilterFactory.equalsTo;
import static org.dashbuilder.dataset.sort.SortOrder.*;
import static org.jbpm.workbench.es.model.ExecutionErrorDataSetConstants.*;

@Dependent
@WorkbenchScreen(identifier = ErrorManagementPerspective.EXECUTION_ERROR_LIST)
public class ExecutionErrorListPresenter extends AbstractMultiGridPresenter<ExecutionErrorSummary, ExecutionErrorListPresenter.ExecutionErrorListView> {

    private final Constants constants = Constants.INSTANCE;

    private List<ExecutionErrorSummary> visibleExecutionErrors = new ArrayList<ExecutionErrorSummary>();
    @Inject
    private ErrorPopupPresenter errorPopup;
    @Inject
    private Caller<ExecutorService> executorServices;
    @Inject
    private Event<ExecErrorSelectionEvent> execErrorSelectionEvent;
    @Inject
    private Event<ExecErrorChangedEvent> execErrorChangedEvent;

    @Override
    public void getData(final Range visibleRange) {
        try {
            if (!isAddingDefaultFilters()) {
                FilterSettings currentTableSettings = dataSetQueryHelper.getCurrentTableSettings();
                currentTableSettings.setServerTemplateId(getSelectedServerTemplate());
                currentTableSettings.setTablePageSize(view.getListGrid().getPageSize());
                ColumnSortList columnSortList = view.getListGrid().getColumnSortList();
                if (columnSortList != null && columnSortList.size() > 0) {
                    dataSetQueryHelper.setLastOrderedColumn(columnSortList.size() > 0 ? columnSortList.get(0).getColumn().getDataStoreName() : "");
                    dataSetQueryHelper.setLastSortOrder(columnSortList.size() > 0 && columnSortList.get(0).isAscending() ? ASCENDING : DESCENDING);
                } else {
                    dataSetQueryHelper.setLastOrderedColumn(COLUMN_ERROR_DATE);
                    dataSetQueryHelper.setLastSortOrder(ASCENDING);
                }

                dataSetQueryHelper.setDataSetHandler(currentTableSettings);
                dataSetQueryHelper.lookupDataSet(visibleRange.getStart(),
                                                 new AbstractDataSetReadyCallback(errorPopup,
                                                                                  view,
                                                                                  currentTableSettings.getUUID()) {
                                                     @Override
                                                     public void callback(DataSet dataSet) {
                                                         if (dataSet != null && dataSetQueryHelper.getCurrentTableSettings().getKey().equals(currentTableSettings.getKey())) {
                                                             visibleExecutionErrors.clear();
                                                             for (int i = 0; i < dataSet.getRowCount(); i++) {
                                                                 visibleExecutionErrors.add(createExecutionErrorSummaryFromDataSet(dataSet,
                                                                                                                                   i));
                                                             }

                                                             boolean lastPageExactCount = false;
                                                             if (dataSet.getRowCount() < view.getListGrid().getPageSize()) {
                                                                 lastPageExactCount = true;
                                                             }
                                                             updateDataOnCallback(visibleExecutionErrors,
                                                                                  visibleRange.getStart(),
                                                                                  visibleRange.getStart() + visibleExecutionErrors.size(),
                                                                                  lastPageExactCount);
                                                         }
                                                     }
                                                 });
                view.hideBusyIndicator();
            }
        } catch (Exception e) {
            errorPopup.showMessage(Constants.INSTANCE.Error() + " " + e.getMessage());
            view.hideBusyIndicator();
        }
    }

    protected ExecutionErrorSummary createExecutionErrorSummaryFromDataSet(final DataSet dataSet,
                                                                           final Integer index) {
        return new ExecutionErrorSummary(
                dataSetQueryHelper.getColumnStringValue(dataSet,
                                                        COLUMN_ERROR_ID,
                                                        index),
                dataSetQueryHelper.getColumnStringValue(dataSet,
                                                        COLUMN_ERROR_TYPE,
                                                        index),
                dataSetQueryHelper.getColumnStringValue(dataSet,
                                                        COLUMN_DEPLOYMENT_ID,
                                                        index),
                dataSetQueryHelper.getColumnLongValue(dataSet,
                                                      COLUMN_PROCESS_INST_ID,
                                                      index),
                dataSetQueryHelper.getColumnStringValue(dataSet,
                                                        COLUMN_PROCESS_ID,
                                                        index),
                dataSetQueryHelper.getColumnLongValue(dataSet,
                                                      COLUMN_ACTIVITY_ID,
                                                      index),
                dataSetQueryHelper.getColumnStringValue(dataSet,
                                                        COLUMN_ACTIVITY_NAME,
                                                        index),
                dataSetQueryHelper.getColumnLongValue(dataSet,
                                                      COLUMN_JOB_ID,
                                                      index),
                dataSetQueryHelper.getColumnStringValue(dataSet,
                                                        COLUMN_ERROR_MSG,
                                                        index),
                dataSetQueryHelper.getColumnBooleanValue(dataSet,
                                                         COLUMN_ERROR_ACK,
                                                         index),
                dataSetQueryHelper.getColumnStringValue(dataSet,
                                                        COLUMN_ERROR_ACK_BY,
                                                        index),
                dataSetQueryHelper.getColumnDateValue(dataSet,
                                                      COLUMN_ERROR_ACK_AT,
                                                      index),
                dataSetQueryHelper.getColumnDateValue(dataSet,
                                                      COLUMN_ERROR_DATE,
                                                      index)
        );
    }

    public void onExecErrorChanged(@Observes ExecErrorChangedEvent errorChangedEvent) {
        refreshGrid();
    }

    public void acknowledgeExecutionError(final String executionErrorId,
                                          final String deploymentId) {
        executorServices.call((Void nothing) -> {
            view.displayNotification(Constants.INSTANCE.ExecutionErrorAcknowledged(executionErrorId));
            execErrorChangedEvent.fire(new ExecErrorChangedEvent(getSelectedServerTemplate(),
                                                                 deploymentId,
                                                                 executionErrorId));
            refreshGrid();
        }).acknowledgeError(getSelectedServerTemplate(),
                            deploymentId,
                            executionErrorId);
    }

    public void bulkAcknowledge(List<ExecutionErrorSummary> execErrorsSelected) {
        if (execErrorsSelected == null || execErrorsSelected.isEmpty()) {
            return;
        }
        for (ExecutionErrorSummary selected : execErrorsSelected) {
            if (selected.isAcknowledged()) {
                view.displayNotification("Error " + selected.getErrorId() + "is already acknowledge");
                continue;
            } else {
                acknowledgeExecutionError(selected.getErrorId(),
                                          selected.getDeploymentId());
            }
        }
    }

    @WorkbenchPartTitle
    public String getTitle() {
        return Constants.INSTANCE.ExecutionErrors();
    }

    @WorkbenchMenu
    public Menus getMenus() {
        return MenuFactory
                .newTopLevelCustomMenu(serverTemplateSelectorMenuBuilder).endMenu()
                .newTopLevelCustomMenu(new RefreshMenuBuilder(this)).endMenu()
                .newTopLevelCustomMenu(refreshSelectorMenuBuilder).endMenu()
                .newTopLevelCustomMenu(new RestoreDefaultFiltersMenuBuilder(this)).endMenu()
                .build();
    }

    protected List<ExecutionErrorSummary> getDisplayedExecutionErrors() {
        return visibleExecutionErrors;
    }

    public void selectExecutionError(final ExecutionErrorSummary summary,
                                     final Boolean close) {
        PlaceStatus status = placeManager.getStatus(new DefaultPlaceRequest(ErrorManagementPerspective.EXECUTION_ERROR_DETAILS));
        if (status == PlaceStatus.CLOSE) {
            placeManager.goTo(ErrorManagementPerspective.EXECUTION_ERROR_DETAILS);
            execErrorSelectionEvent.fire(new ExecErrorSelectionEvent(getSelectedServerTemplate(),
                                                                     summary.getDeploymentId(),
                                                                     summary.getErrorId()));
        } else if (status == PlaceStatus.OPEN && !close) {
            execErrorSelectionEvent.fire(new ExecErrorSelectionEvent(getSelectedServerTemplate(),
                                                                     summary.getDeploymentId(),
                                                                     summary.getErrorId()));
        } else if (status == PlaceStatus.OPEN && close) {
            placeManager.closePlace(ErrorManagementPerspective.EXECUTION_ERROR_DETAILS);
        }
    }

    public void onExecutionErrorSelectionEvent(@Observes ExecErrorWithDetailsRequestEvent event) {
        placeManager.goTo(ErrorManagementPerspective.EXECUTION_ERROR_DETAILS);
        execErrorSelectionEvent.fire(new ExecErrorSelectionEvent(event.getServerTemplateId(),
                                                                 event.getDeploymentId(),
                                                                 event.getErrorId()));
    }

    @Inject
    public void setExecutorServices(final Caller<ExecutorService> executorServices) {
        this.executorServices = executorServices;
    }

    @Override
    public void setupAdvancedSearchView() {
        view.addNumericFilter(constants.Id(),
                              constants.FilterByErrorId(),
                              v -> addAdvancedSearchFilter(equalsTo(COLUMN_ERROR_ID,
                                                                    v)),
                              v -> removeAdvancedSearchFilter(equalsTo(COLUMN_ERROR_ID,
                                                                       v))
        );

        view.addNumericFilter(constants.Process_Instance_Id(),
                              constants.FilterByProcessInstanceId(),
                              v -> addAdvancedSearchFilter(equalsTo(COLUMN_PROCESS_INST_ID,
                                                                    v)),
                              v -> removeAdvancedSearchFilter(equalsTo(COLUMN_PROCESS_INST_ID,
                                                                       v))
        );

        view.addNumericFilter(constants.JobId(),
                              constants.FilterByJobId(),
                              v -> addAdvancedSearchFilter(equalsTo(COLUMN_JOB_ID,
                                                                    v)),
                              v -> removeAdvancedSearchFilter(equalsTo(COLUMN_JOB_ID,
                                                                       v))
        );

        final Map<String, String> states = new HashMap<>();
        states.put(String.valueOf(ExecutionErrorType.DB),
                   constants.DB());
        states.put(String.valueOf(ExecutionErrorType.TASK),
                   constants.Task());
        states.put(String.valueOf(ExecutionErrorType.PROCESS),
                   constants.Process());
        view.addSelectFilter(constants.Type(),
                             states,
                             false,
                             v -> addAdvancedSearchFilter(equalsTo(COLUMN_ERROR_TYPE,
                                                                   v)),
                             v -> removeAdvancedSearchFilter(equalsTo(COLUMN_ERROR_TYPE,
                                                                      v))
        );

        final Map<String, String> acks = new HashMap<>();
        final org.jbpm.workbench.common.client.resources.i18n.Constants constants = org.jbpm.workbench.common.client.resources.i18n.Constants.INSTANCE;
        acks.put(String.valueOf(Boolean.TRUE),
                 constants.Yes());
        acks.put(String.valueOf(Boolean.FALSE),
                 constants.No());
        view.addSelectFilter(this.constants.Acknowledged(),
                             acks,
                             false,
                             v -> addAdvancedSearchFilter(equalsTo(COLUMN_ERROR_ACK,
                                                                   v)),
                             v -> removeAdvancedSearchFilter(equalsTo(COLUMN_ERROR_ACK,
                                                                      v))
        );

        view.addDateRangeFilter(this.constants.ErrorDate(),
                                v -> addAdvancedSearchFilter(between(COLUMN_ERROR_DATE,
                                                                     v.getStartDate(),
                                                                     v.getEndDate())),
                                v -> removeAdvancedSearchFilter(between(COLUMN_ERROR_DATE,
                                                                        v.getStartDate(),
                                                                        v.getEndDate()))
        );

        view.addActiveFilter(
                this.constants.Acknowledged(),
                constants.No(),
                String.valueOf(Boolean.FALSE),
                v -> removeAdvancedSearchFilter(equalsTo(COLUMN_ERROR_ACK,
                                                         v))

        );
    }

    /*-------------------------------------------------*/
    /*---              DashBuilder                   --*/
    /*-------------------------------------------------*/
    @Override
    public FilterSettings createTableSettingsPrototype() {
        FilterSettingsBuilderHelper builder = FilterSettingsBuilderHelper.init();
        builder.initBuilder();
        builder.dataset(EXECUTION_ERROR_LIST_DATASET);

        builder.filterOn(true,
                         true,
                         true);
        builder.tableOrderEnabled(true);
        builder.tableOrderDefault(COLUMN_ERROR_DATE,
                                  DESCENDING);
        builder.tableWidth(1000);

        return builder.buildSettings();
    }

    private FilterSettings createFilterTabSettings(final Boolean acknowledged) {
        FilterSettingsBuilderHelper builder = FilterSettingsBuilderHelper.init();
        builder.initBuilder();

        builder.dataset(EXECUTION_ERROR_LIST_DATASET);

        if (acknowledged != null) {
            builder.filter(equalsTo(COLUMN_ERROR_ACK,
                                    acknowledged));
        }

        builder.filterOn(true,
                         true,
                         true);
        builder.tableOrderEnabled(true);
        builder.tableOrderDefault(COLUMN_ERROR_DATE,
                                  DESCENDING);
        addCommonColumnSettings(builder);

        return builder.buildSettings();
    }

    public FilterSettings createAllTabSettings() {
        return createFilterTabSettings(null);
    }

    public FilterSettings createAcknowledgedTabSettings() {
        return createFilterTabSettings(Boolean.TRUE);
    }

    public FilterSettings createNewTabSettings() {
        return createFilterTabSettings(Boolean.FALSE);
    }

    @Override
    public FilterSettings createSearchTabSettings() {
        return createFilterTabSettings(Boolean.FALSE);
    }

    protected void addCommonColumnSettings(FilterSettingsBuilderHelper builder) {
        builder.setColumn(COLUMN_ERROR_ACK,
                          constants.Ack());
        builder.setColumn(COLUMN_ERROR_ACK_AT,
                          constants.AckAt());
        builder.setColumn(COLUMN_ERROR_ACK_BY,
                          constants.AckBy());
        builder.setColumn(COLUMN_ACTIVITY_ID,
                          constants.ActivityId());
        builder.setColumn(COLUMN_ACTIVITY_NAME,
                          constants.ActivityName());
        builder.setColumn(COLUMN_DEPLOYMENT_ID,
                          constants.DeploymentId());
        builder.setColumn(COLUMN_ERROR_DATE,
                          constants.Date());
        builder.setColumn(COLUMN_ERROR_ID,
                          constants.Id());
        builder.setColumn(COLUMN_ERROR_MSG,
                          constants.Message());
        builder.setColumn(COLUMN_JOB_ID,
                          constants.JobId());
        builder.setColumn(COLUMN_PROCESS_ID,
                          constants.ProcessId());
        builder.setColumn(COLUMN_PROCESS_INST_ID,
                          constants.Process_Instance_Id());
        builder.setColumn(COLUMN_ERROR_TYPE,
                          constants.Type());
    }

    public interface ExecutionErrorListView extends MultiGridView<ExecutionErrorSummary, ExecutionErrorListPresenter> {

    }
}
