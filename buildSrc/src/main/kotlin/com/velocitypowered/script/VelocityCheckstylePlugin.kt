/*
 * Copyright (C) 2018 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.script

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class VelocityCheckstylePlugin : Plugin<Project> {
  override fun apply(target: Project) = target.configure()
  private fun Project.configure() {
    apply<CheckstylePlugin>()
    extensions.configure<CheckstyleExtension> {
      configProperties["configDirectory"] = project.rootProject.file("config/checkstyle")
      maxErrors = 0
      maxWarnings = 0
    }
  }
}
