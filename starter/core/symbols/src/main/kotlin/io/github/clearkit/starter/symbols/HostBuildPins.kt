package io.github.clearkit.starter.symbols

import io.github.clearkit.starter.model.HostBuild
import io.github.wzgrx.dexadapt.Pin
import io.github.wzgrx.dexadapt.Symbol
import io.github.wzgrx.dexadapt.absentIn
import io.github.wzgrx.dexadapt.pin

/** 按【宿主】内置版本钉死位置（DexAdapt 的构建标识用 [HostBuild.name]）。只对这个已知版本生效，本机适配时忽略。 */
fun <S : Symbol> S.pin(build: HostBuild, pin: Pin): S = pin(build.name, pin)

/** 确认这些内置版本没有这个符号（该版本没有这个功能，例如某个渠道没有的入口）。 */
fun <S : Symbol> S.absentIn(vararg builds: HostBuild): S = absentIn(*builds.map { it.name }.toTypedArray())
