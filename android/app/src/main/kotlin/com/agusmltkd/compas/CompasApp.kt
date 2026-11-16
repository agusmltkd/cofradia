package com.agusmltkd.compas

import android.app.Application
import com.agusmltkd.compas.datos.Almacen
import com.agusmltkd.compas.datos.Api
import com.agusmltkd.compas.datos.Repositorio

class CompasApp : Application() {
    lateinit var repositorio: Repositorio
        private set

    override fun onCreate() {
        super.onCreate()
        repositorio = Repositorio(Almacen(this), Api())
    }
}
