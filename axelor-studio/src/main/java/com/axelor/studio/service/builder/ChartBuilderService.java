/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.studio.service.builder;

import com.axelor.common.Inflector;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.MetaJsonRecord;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.MetaView;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.meta.loader.XMLViews;
import com.axelor.meta.schema.ObjectViews;
import com.axelor.studio.db.ChartBuilder;
import com.axelor.studio.db.Filter;
import com.axelor.studio.exception.IExceptionMessage;
import com.axelor.studio.service.StudioMetaService;
import com.axelor.studio.service.filter.FilterCommonService;
import com.axelor.studio.service.filter.FilterSqlService;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class generate charts using ViewBuilder and chart related fields. Chart xml generated by
 * adding query, search fields , onInit actions..etc. All filters with parameter checked will be
 * used as search fields. Tags also there to use context variable in filter value, like $User for
 * current user (__user__).
 */
public class ChartBuilderService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String Tab1 = "\n \t";
  private static final String Tab2 = "\n \t\t";
  private static final String Tab3 = "\n \t\t\t";
  private static final List<String> dateTypes =
      Arrays.asList(
          new String[] {"DATE", "DATETIME", "LOCALDATE", "LOCALDATETIME", "ZONNEDDATETIME"});

  private List<String> searchFields;

  //	private List<RecordField> onNewFields;

  private List<String> joins;

  private String categType;

  @Inject private MetaModelRepository metaModelRepo;

  @Inject private FilterSqlService filterSqlService;

  @Inject private FilterCommonService filterCommonService;

  @Inject private StudioMetaService metaService;

  /**
   * Root Method to access the service it generate AbstractView from ViewBuilder.
   *
   * @param viewBuilder ViewBuilder object of type chart.
   * @return AbstractView from meta schema.
   * @throws JAXBException
   * @throws AxelorException
   */
  public void build(ChartBuilder chartBuilder) throws JAXBException, AxelorException {

    if (chartBuilder.getName().contains(" ")) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get(IExceptionMessage.CHART_BUILDER_1));
    }

    searchFields = new ArrayList<String>();
    //		onNewFields = new ArrayList<RecordField>();
    joins = new ArrayList<String>();
    categType = "text";

    String[] queryString = prepareQuery(chartBuilder);
    //		setOnNewAction(chartBuilder);

    String xml = createXml(chartBuilder, queryString);

    log.debug("Chart xml: {}", xml);

    ObjectViews chartView = XMLViews.fromXML(xml);

    MetaView metaView = metaService.generateMetaView(chartView.getViews().get(0));

    if (metaView != null) {
      chartBuilder.setMetaViewGenerated(metaView);
    }
  }

  private String createXml(ChartBuilder chartBuilder, String[] queryString) {

    String xml =
        "<chart name=\"" + chartBuilder.getName() + "\" title=\"" + chartBuilder.getTitle() + "\" ";

    //		if (onNewAction != null) {
    //			xml += " onInit=\"" + onNewAction.getName() + "\" ";
    //		}

    xml += ">\n";

    if (!searchFields.isEmpty()) {
      xml += "\t" + getSearchFields() + "\n";
    }

    String groupLabel =
        chartBuilder.getIsJsonGroupOn()
            ? chartBuilder.getGroupOnJson().getTitle()
            : chartBuilder.getGroupOn().getLabel();

    String displayLabel =
        chartBuilder.getIsJsonDisplayField()
            ? chartBuilder.getDisplayFieldJson().getTitle()
            : chartBuilder.getDisplayField().getLabel();

    xml += "\t<dataset type=\"sql\"><![CDATA[";
    xml += Tab2 + queryString[0];
    xml += Tab2 + " ]]></dataset>";
    xml +=
        Tab1
            + "<category key=\"group_field\" type=\""
            + categType
            + "\" title=\""
            + groupLabel
            + "\" />";
    xml +=
        Tab1
            + "<series key=\"sum_field\" type=\""
            + chartBuilder.getChartType()
            + "\" title=\""
            + displayLabel
            + "\" ";
    if (queryString[1] != null) {
      xml += "groupBy=\"agg_field\" ";
    }
    xml += "/>\n";
    xml += "</chart>";

    return xml;
  }

  /**
   * Method create query from chart filters added in chart builder.
   *
   * @param viewBuilder ViewBuilder of type chart
   * @return StringArray with first element as query string and second as aggregate field name.
   * @throws AxelorException
   */
  private String[] prepareQuery(ChartBuilder chartBuilder) throws AxelorException {

    String query =
        createSumQuery(
            chartBuilder.getIsJsonDisplayField(),
            chartBuilder.getDisplayField(),
            chartBuilder.getDisplayFieldJson());

    String groupField =
        getGroup(
            chartBuilder.getIsJsonGroupOn(),
            chartBuilder.getGroupOn(),
            chartBuilder.getGroupOnJson(),
            chartBuilder.getGroupDateType(),
            chartBuilder.getGroupOnTarget());

    String aggField =
        getGroup(
            chartBuilder.getIsJsonAggregateOn(),
            chartBuilder.getAggregateOn(),
            chartBuilder.getAggregateOnJson(),
            chartBuilder.getAggregateDateType(),
            chartBuilder.getAggregateOnTarget());

    query += groupField + " AS group_field";

    if (aggField != null) {
      query += "," + Tab3 + aggField + " AS agg_field";
    }

    String filters = filterSqlService.getSqlFilters(chartBuilder.getFilterList(), joins, true);
    addSearchField(chartBuilder.getFilterList());
    String model = chartBuilder.getModel();

    if (chartBuilder.getIsJson()) {
      if (filters != null) {
        filters = "self.json_model = '" + model + "' AND (" + filters + ")";
      } else {
        filters = "self.json_model = '" + model + "'";
      }
      model = MetaJsonRecord.class.getName();
    }

    query += Tab2 + "FROM " + Tab3 + getTable(model) + " self";

    if (!joins.isEmpty()) {
      query += Tab3 + Joiner.on(Tab3).join(joins);
    }

    if (filters != null) {
      query += Tab2 + "WHERE " + Tab3 + filters;
    }

    query += Tab2 + "group by " + Tab3 + "group_field";

    if (aggField != null && aggField != null) {
      query += ",agg_field";
      return new String[] {query, aggField};
    }

    return new String[] {query, null};
  }

  private String createSumQuery(boolean isJson, MetaField metaField, MetaJsonField jsonField) {

    String sumField = null;
    if (isJson) {
      String sqlType = filterSqlService.getSqlType(jsonField.getType());
      sumField =
          "cast(self."
              + filterSqlService.getColumn(jsonField.getModel(), jsonField.getModelField())
              + "->>'"
              + jsonField.getName()
              + "' as "
              + sqlType
              + ")";
    } else {
      sumField = "self." + filterSqlService.getColumn(metaField);
    }

    return "SELECT" + Tab3 + "SUM(" + sumField + ") AS sum_field," + Tab3;
  }

  private String getGroup(
      boolean isJson, MetaField metaField, MetaJsonField jsonField, String dateType, String target)
      throws AxelorException {

    if (!isJson && metaField == null || isJson && jsonField == null) {
      return null;
    }

    String typeName = null;
    String group = null;
    Object object = null;
    StringBuilder parent = new StringBuilder("self");
    if (isJson) {
      group = jsonField.getName();
      typeName = filterSqlService.getSqlType(jsonField.getType());
      if (target != null) {
        object = filterSqlService.parseJsonField(jsonField, target, joins, parent);
      }
    } else {
      group = filterSqlService.getColumn(metaField);
      typeName = filterSqlService.getSqlType(metaField.getTypeName());
      if (target != null) {
        object = filterSqlService.parseMetaField(metaField, target, joins, parent, true);
      }
    }

    if (object != null) {
      String[] sqlField = filterSqlService.getSqlField(object, parent.toString(), joins);
      typeName = sqlField[1];
      group = sqlField[0];
    }

    log.debug("Group field type: {}, group: {}, dateType: {}", typeName, group, dateType);

    if (dateType != null && typeName != null && dateTypes.contains(typeName.toUpperCase())) {
      group = getDateTypeGroup(dateType, typeName, group);
    }

    return group;
  }

  private String getDateTypeGroup(String dateType, String typeName, String group) {

    switch (dateType) {
      case "year":
        group = "to_char(cast(" + group + " as date), 'yyyy')";
        break;
      case "month":
        group = "to_char(cast(" + group + " as date), 'yyyy-mm')";
        break;
      default:
        categType = "date";
    }

    return group;
  }

  /**
   * Method generate xml for search-fields.
   *
   * @return
   */
  private String getSearchFields() {

    String search = "<search-fields>";

    for (String searchField : searchFields) {
      search += Tab2 + searchField;
    }
    search += Tab1 + "</search-fields>";

    return search;
  }

  /**
   * Method set default value for search-fields(parameters). It will add field and expression in
   * onNew for chart.
   *
   * @param fieldName Name of field of search-field.
   * @param typeName Type of field.
   * @param defaultValue Default value input in chart filter.
   * @param modelField It is for relational field. String array with first element as Model name and
   *     second as its field.
   */
  //	private void setDefaultValue(String fieldName, String typeName,
  //			String defaultValue, String[] modelField) {
  //
  //		if (defaultValue == null) {
  //			return;
  //		}
  //
  //		RecordField field = new RecordField();
  //		field.setName(fieldName);
  //
  //		defaultValue = filterCommonService.getTagValue(defaultValue, false);
  //
  //		if (modelField != null) {
  //			if (typeName.equals("STRING")) {
  //				defaultValue = "__repo__(" + modelField[0]
  //						+ ").all().filter(\"LOWER(" + modelField[1] + ") LIKE "
  //						+ defaultValue + "\").fetchOne()";
  //			} else {
  //				defaultValue = "__repo__(" + modelField[0]
  //						+ ").all().filter(\"" + modelField[1] + " = "
  //						+ defaultValue + "\").fetchOne()";
  //			}
  //
  //		}
  //
  //		log.debug("Default value: {}", defaultValue);
  //
  //		field.setExpression("eval:" + defaultValue);
  //
  //		onNewFields.add(field);
  //	}

  /**
   * It will create onNew action from onNew fields.
   *
   * @param viewBuilder ViewBuilder use to get model name also used in onNew action name creation.
   */
  //	private void setOnNewAction(ChartBuilder chartBuilder) {
  //
  //		if (!onNewFields.isEmpty()) {
  //			onNewAction = new ActionRecord();
  //			onNewAction.setName("action-" + chartBuilder.getName() + "-default");
  //			onNewAction.setModel(chartBuilder.getModel());
  //			onNewAction.setFields(onNewFields);
  //		}
  //
  //	}

  private void addSearchField(List<Filter> filters) throws AxelorException {

    if (filters == null) {
      return;
    }

    for (Filter filter : filters) {
      if (!filter.getIsParameter()) {
        continue;
      }
      String fieldStr = "param" + filter.getId();

      Object object = null;
      StringBuilder parent = new StringBuilder("self");
      if (filter.getIsJson()) {
        object =
            filterSqlService.parseJsonField(
                filter.getMetaJsonField(), filter.getTargetField(), null, parent);
      } else {
        object =
            filterSqlService.parseMetaField(
                filter.getMetaField(), filter.getTargetField(), null, parent, true);
      }

      if (object instanceof MetaField) {
        fieldStr = getMetaSearchField(fieldStr, (MetaField) object);
      } else {
        fieldStr = getJsonSearchField(fieldStr, (MetaJsonField) object);
      }

      searchFields.add(fieldStr + "\" x-required=\"true\" />");
    }
  }

  private String getMetaSearchField(String fieldStr, MetaField field) {

    fieldStr = "<field name=\"" + fieldStr + "\" title=\"" + field.getLabel();

    if (field.getRelationship() == null) {
      String fieldType = filterCommonService.getFieldType(field);
      fieldStr += "\" type=\"" + fieldType;
    } else {
      String[] targetRef = filterSqlService.getDefaultTarget(field.getName(), field.getTypeName());
      String[] nameField = targetRef[0].split("\\.");
      fieldStr +=
          "\" widget=\"ref-text\" type=\""
              + filterCommonService.getFieldType(targetRef[1])
              + "\" x-target-name=\""
              + nameField[1]
              + "\" x-target=\""
              + metaModelRepo.findByName(field.getTypeName()).getFullName();
    }

    return fieldStr;
  }

  private String getJsonSearchField(String fieldStr, MetaJsonField field) {

    fieldStr = "<field name=\"" + fieldStr + "\" title=\"" + field.getTitle();

    if (field.getTargetJsonModel() != null) {
      String[] targetRef =
          filterSqlService.getDefaultTargetJson(field.getName(), field.getTargetJsonModel());
      String[] nameField = targetRef[0].split("\\.");
      fieldStr +=
          "\" widget=\"ref-text\" type=\""
              + filterCommonService.getFieldType(targetRef[1])
              + "\" x-target-name=\""
              + nameField[1]
              + "\" x-target=\""
              + MetaJsonRecord.class.getName()
              + "\" x-domain=\"self.jsonModel = '"
              + field.getTargetJsonModel().getName()
              + "'";
    } else if (field.getTargetModel() != null) {
      String[] targetRef =
          filterSqlService.getDefaultTarget(field.getName(), field.getTargetModel());
      String[] nameField = targetRef[0].split("\\.");
      fieldStr +=
          "\" widget=\"ref-text\" type=\""
              + filterCommonService.getFieldType(targetRef[1])
              + "\" x-target-name=\""
              + nameField[1]
              + "\" x-target=\""
              + field.getTargetModel();
    } else {
      String fieldType = Inflector.getInstance().camelize(field.getType(), true);
      fieldStr += "\" type=\"" + fieldType;
    }

    return fieldStr;
  }

  private String getTable(String model) {

    String[] models = model.split("\\.");
    MetaModel metaModel = metaModelRepo.findByName(models[models.length - 1]);

    if (metaModel != null) {
      return metaModel.getTableName();
    }

    return null;
  }

  public String getDefaultTarget(MetaField metaField) {

    if (metaField.getRelationship() == null) {
      return metaField.getName();
    }

    return filterSqlService.getDefaultTarget(metaField.getName(), metaField.getTypeName())[0];
  }

  public String getDefaultTarget(MetaJsonField metaJsonField) {

    if (!"many-to-one,one-to-one,json-many-to-one".contains(metaJsonField.getType())) {
      return metaJsonField.getName();
    }

    if (metaJsonField.getTargetJsonModel() != null) {
      return filterSqlService
          .getDefaultTargetJson(metaJsonField.getName(), metaJsonField.getTargetJsonModel())[0];
    }

    if (metaJsonField.getTargetModel() == null) {
      return metaJsonField.getName();
    }

    return filterSqlService
        .getDefaultTarget(metaJsonField.getName(), metaJsonField.getTargetModel())[0];
  }

  public String getTargetType(Object object, String target) {

    if (target == null) {
      log.debug("No target provided for target type");
      return null;
    }

    Object targetField = null;
    try {
      if (object instanceof MetaJsonField) {
        targetField = filterSqlService.parseJsonField((MetaJsonField) object, target, null, null);
      } else if (object instanceof MetaField) {
        targetField = filterSqlService.parseMetaField((MetaField) object, target, null, null, true);
      }
    } catch (AxelorException e) {
    }

    if (targetField == null) {
      log.debug("Target field not found");
      return null;
    }

    log.debug("Target field found: {}", targetField);

    return filterSqlService.getTargetType(targetField);
  }
}
