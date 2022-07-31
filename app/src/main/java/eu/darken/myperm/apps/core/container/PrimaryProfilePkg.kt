package eu.darken.myperm.apps.core.container

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import eu.darken.myperm.R
import eu.darken.myperm.apps.core.AppRepo
import eu.darken.myperm.apps.core.Pkg
import eu.darken.myperm.apps.core.features.*
import eu.darken.myperm.apps.core.getIcon2
import eu.darken.myperm.apps.core.getLabel2
import eu.darken.myperm.common.debug.logging.log
import eu.darken.myperm.permissions.core.Permission
import eu.darken.myperm.permissions.core.known.APerm

data class PrimaryProfilePkg(
    override val packageInfo: PackageInfo,
    override val userHandle: UserHandle = Process.myUserHandle(),
    override val installerInfo: InstallerInfo,
) : BasePkg(), HasPermissionUseInfo {

    override val id: Pkg.Id = Pkg.Id(packageInfo.packageName, userHandle)

    private var _label: String? = null
    override fun getLabel(context: Context): String {
        _label?.let { return it }
        val newLabel = context.packageManager.getLabel2(id)
            ?: twins.firstNotNullOfOrNull { it.getLabel(context) }
            ?: super.getLabel(context)
            ?: id.pkgName
        _label = newLabel
        return newLabel
    }

    override fun getIcon(context: Context): Drawable =
        context.packageManager.getIcon2(id)
            ?: twins.firstNotNullOfOrNull { it.getIcon(context) }
            ?: super.getIcon(context)
            ?: context.getDrawable(R.drawable.ic_default_app_icon_24)!!

    override var siblings: Collection<Pkg> = emptyList()
    override var twins: Collection<Installed> = emptyList()

    override val requestedPermissions: Collection<UsedPermissionStateful> by lazy {
        packageInfo.requestedPermissions?.mapIndexed { index, permissionId ->
            val flags = packageInfo.requestedPermissionsFlags[index]

            UsedPermissionStateful(
                id = Permission.Id(permissionId),
                flags = flags,
            )
        } ?: emptyList()
    }

    override val declaredPermissions: Collection<PermissionInfo> by lazy {
        packageInfo.permissions?.toSet() ?: emptyList()
    }

    override val internetAccess: InternetAccess by lazy {
        when {
            isSystemApp || getPermissionUses(APerm.INTERNET.id)?.isGranted == true -> InternetAccess.DIRECT
            siblings.any { it is HasPermissionUseInfo && it.getPermissionUses(APerm.INTERNET.id)?.isGranted == true } -> InternetAccess.INDIRECT
            else -> InternetAccess.NONE
        }
    }
}

private fun PackageInfo.toNormalPkg(context: Context): PrimaryProfilePkg = PrimaryProfilePkg(
    packageInfo = this,
    installerInfo = getInstallerInfo(context.packageManager),
)

fun Context.getNormalPkgs(): Collection<BasePkg> {
    log(AppRepo.TAG) { "getNormalPkgs()" }

    return packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS).map {
        it.toNormalPkg(this)
            .also { log(AppRepo.TAG) { "PKG[normal]: $it" } }
    }
}