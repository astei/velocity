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

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.withType

class VelocityJavadocPlugin : Plugin<Project> {
  override fun apply(target: Project) = target.configure()
  private fun Project.configure() {
    apply<JavaBasePlugin>()
    tasks.withType<Javadoc> {
      with(options as StandardJavadocDocletOptions) {
        encoding = "UTF-8"
        source = "8"
        links(
          "https://www.slf4j.org/apidocs/",
          "https://google.github.io/guava/releases/30.0-jre/api/docs/",
          "https://google.github.io/guice/api-docs/4.2/javadoc/",
          "https://docs.oracle.com/javase/8/docs/api/",
          "https://jd.adventure.kyori.net/api/4.7.0/"
        )
        // Disable the crazy super-strict doclint tool in Java 8
        //addStringOption("Xdoclink:none", "-quet")
        // Mark sources as Java 8 source compatible
        source = "8"
        // Remove 'undefined' from search paths when generating javadoc for a non-modular project (JDK-8215291)
        if (JavaVersion.current() >= JavaVersion.VERSION_1_9 && JavaVersion.current() < JavaVersion.VERSION_12) {
          addBooleanOption("-no-module-directories", true)
        }
      }
    }
  }
}
