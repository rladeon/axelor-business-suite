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
package com.axelor.apps.sale.service.configurator;

import com.axelor.apps.sale.db.ConfiguratorCreator;
import com.axelor.apps.sale.db.ConfiguratorFormula;
import com.axelor.exception.AxelorException;
import com.axelor.meta.db.MetaField;

public interface ConfiguratorFormulaService {

  /** Return the filled MetaField */
  MetaField getMetaField(ConfiguratorFormula configuratorFormula);

  /** Return the filled MetaField */
  MetaField getMetaField(
      ConfiguratorFormula configuratorFormula, ConfiguratorCreator configuratorCreator);

  /**
   * Check if the written formula is valid.
   *
   * @param formula
   * @param creator
   */
  void checkFormula(ConfiguratorFormula formula, ConfiguratorCreator creator)
      throws AxelorException;
}
