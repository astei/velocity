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
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.withType
import java.io.ByteArrayOutputStream

class SetManifestImplVersionPlugin : Plugin<Project> {
  override fun apply(target: Project) = target.afterEvaluate { configure() }
  private fun Project.configure() {
    val currentShortRevision = ByteArrayOutputStream().use {
      exec {
        executable = "git"
        args = listOf("rev-parse", "HEAD")
        standardOutput = it
      }
      it.toString().trim().substring(0, 8)
    }
    tasks.withType<Jar> {
      manifest {
        val buildNumber = System.getenv("BUILD_NUMBER")
        if (project.version.toString().endsWith("-SNAPSHOT")) {
          if (buildNumber != null) {
            archiveVersion.set("${project.version}+g$currentShortRevision-b${buildNumber}")
          } else {
            archiveVersion.set("${project.version}+g$currentShortRevision")
          }
        } else {
          archiveVersion.set(project.version.toString())
        }
        attributes["Implementation-Version"] = archiveVersion.get()
      }
    }
  }
}
