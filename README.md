# sbt-hepek

Welcome to **sbt-hepek**, an [sbt](http://www.scala-sbt.org) plugin for rendering Scala `object`s to files.

## Installing

Sbt-hepek is just like any other plugin for sbt, the installation is done by adding following lines to the `project/plugins.sbt` file:

```scala
resolvers += Resolver.sonatypeRepo("snapshots")
addSbtPlugin("ba.sake" % "sbt-hepek" % "0.0.1-SNAPSHOT")
```

and enabling it in your `build.sbt`: 

```scala
enablePlugins(HepekPlugin)
//logLevel in hepek := Level.Debug // enable to see which objects are rendered
```

 Note that this is the **first version** of this plugin, currently in development (SNAPSHOT).

## Using

There is one goal of sbt-hepek, called `hepek`.  
When executed, it will render all Scala `object`s that extend [`Renderable`](https://github.com/sake92/hepek-core/blob/master/src/main/java/ba/sake/hepek/core/Renderable.java) 
trait to respective files, relative to the `hepekTarget` folder.  

Example:

```scala
import java.io.File
import ba.sake.hepek.core.Renderable

object RenderMe extends Renderable {

  override def render: String = {
    // arbitrary Scala code
    "Some text..."
  }
  
  override def relPath: File = new File("renderme.txt")
}
```

Default value for `hepekTarget` is `hepekTarget := target.value / "web" / "public" / "main"`.  
The good old `target` folder.  
When you run `sbt hepek` task, you'll find the `renderme.txt` file in the `target/web/public/main` folder with contents you specified by the `render` method.

## Docs
- ["Philosophy"](https://dev.to/sake_92/render-static-site-from-scala-code)
- [my website (tutorials in Bosnian)](https://github.com/sake92/sake-ba-source) rendered @ [sake.ba](https://sake.ba)
- [hepek examples](https://github.com/sake92/hepek-examples)

## About the name

A "hepek" in Bosnian language is a jargon for a thing/thingy/stuff...  
It is used when we don't know the name of a thing: "Give me that ... *hepek*".  
Also, it is used in the famous show called "Top lista nadrealista" as a name for an advanced device which calms down situations of various kinds.  

[![IMAGE ALT TEXT HERE](http://img.youtube.com/vi/Jc9SeKu-YwQ/0.jpg)](https://youtu.be/Jc9SeKu-YwQ?t=2m11s)

## Fun fact
I think that this is the first project that tried this approach, namely, using first-class Scala `object`s for this kind of stuff.  
Correct me if I'm wrong... ^_^

## Contact

Author of the plugin is Sakib Hadžiavdić.  
Twitter: @[sake_92](https://twitter.com/sake_92)  
Email: sakib@sake.ba

## License
This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).
